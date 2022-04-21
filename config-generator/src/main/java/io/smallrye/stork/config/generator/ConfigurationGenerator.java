package io.smallrye.stork.config.generator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.google.auto.service.AutoService;

import io.smallrye.stork.api.config.LoadBalancerAttribute;
import io.smallrye.stork.api.config.LoadBalancerAttributes;
import io.smallrye.stork.api.config.LoadBalancerType;
import io.smallrye.stork.api.config.ServiceDiscoveryAttribute;
import io.smallrye.stork.api.config.ServiceDiscoveryAttributes;
import io.smallrye.stork.api.config.ServiceDiscoveryType;
import io.smallrye.stork.api.config.ServiceRegistrarAttribute;
import io.smallrye.stork.api.config.ServiceRegistrarAttributes;
import io.smallrye.stork.api.config.ServiceRegistrarType;
import io.smallrye.stork.spi.LoadBalancerProvider;
import io.smallrye.stork.spi.ServiceDiscoveryProvider;
import io.smallrye.stork.spi.ServiceRegistrarProvider;
import io.smallrye.stork.spi.internal.LoadBalancerLoader;
import io.smallrye.stork.spi.internal.ServiceDiscoveryLoader;
import io.smallrye.stork.spi.internal.ServiceRegistrarLoader;

@SupportedAnnotationTypes({
        "io.smallrye.stork.api.config.LoadBalancerType",
        "io.smallrye.stork.api.config.LoadBalancerAttribute",
        "io.smallrye.stork.api.config.LoadBalancerAttributes",
        "io.smallrye.stork.api.config.ServiceDiscoveryType",
        "io.smallrye.stork.api.config.ServiceDiscoveryAttribute",
        "io.smallrye.stork.api.config.ServiceDiscoveryAttributes"
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class ConfigurationGenerator extends AbstractProcessor {

    public static final LoadBalancerAttribute[] EMPTY_LB_ATTRIBUTES = new LoadBalancerAttribute[0];
    public static final ServiceDiscoveryAttribute[] EMPTY_SD_ATTRIBUTES = new ServiceDiscoveryAttribute[0];
    public static final ServiceRegistrarAttribute[] EMPTY_SR_ATTRIBUTES = new ServiceRegistrarAttribute[0];
    private volatile boolean invoked;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (invoked) {
            return true;
        }
        invoked = true;

        ConfigClassWriter configWriter = new ConfigClassWriter(processingEnv);
        DocWriter docWriter = new DocWriter(processingEnv);

        writeLoadBalancerConfigs(roundEnv, configWriter, docWriter);
        writeConfigWithTypes(roundEnv, configWriter, docWriter);
        writeServiceRegistrarConfigs(roundEnv, configWriter, docWriter);

        return false;
    }

    private void writeServiceRegistrarConfigs(RoundEnvironment roundEnv, ConfigClassWriter configWriter, DocWriter docWriter) {
        Set<Element> serviceRegistrars = collectElementsAnnotatedWith(roundEnv, ServiceRegistrarType.class);
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeMirror registrarProviderType = typeUtils.erasure(
                elementUtils.getTypeElement(ServiceRegistrarProvider.class.getName()).asType());

        Set<String> loaders = new HashSet<>();
        Set<String> types = new HashSet<>(); // to check if there are no two service discovery providers for the same type
        try {
            for (Element element : serviceRegistrars) {
                if (element.getKind() != ElementKind.CLASS) {
                    throw new IllegalArgumentException(
                            "ServiceRegistrarType annotation can only be used on the class level, found one on " + element);
                }

                TypeMirror elementType = elementUtils.getTypeElement(element.toString()).asType();
                if (!typeUtils.isAssignable(elementType, registrarProviderType)) {
                    throw new IllegalArgumentException(
                            "ServiceRegistrarType should be used on ServiceRegistrarProvider classes, found one on " + element);
                }

                ServiceRegistrarAttribute[] attributes = EMPTY_SR_ATTRIBUTES;
                ServiceRegistrarAttributes groupAnnoInstance = element.getAnnotation(ServiceRegistrarAttributes.class);
                if (groupAnnoInstance != null) {
                    attributes = groupAnnoInstance.value();
                } else {
                    ServiceRegistrarAttribute singleAnnotationInstance = element.getAnnotation(ServiceRegistrarAttribute.class);
                    if (singleAnnotationInstance != null) {
                        attributes = new ServiceRegistrarAttribute[] { singleAnnotationInstance };
                    }
                }
                validate(element.toString(), attributes);
                ServiceRegistrarType serviceRegistrarType = element.getAnnotation(ServiceRegistrarType.class);
                String type = serviceRegistrarType.value();
                String metadataKeyClass = extractMetadataKeyClass(element);

                String configClassName = configWriter.createConfig(element, type, attributes);

                loaders.add(
                        configWriter.createServiceRegistrarLoader(element, metadataKeyClass, configClassName, type));
                if (!types.add(type)) {
                    throw new IllegalArgumentException("Multiple classes found for service discovery type: " + type);
                }

                docWriter.createAttributeTable(type, attributes);
            }
            configWriter.createServiceLoaderFile(ServiceRegistrarLoader.class.getName(), loaders);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate configuration classes", e);
        }
    }

    private String extractMetadataKeyClass(Element element) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (ServiceRegistrarType.class.getName().equals(annotationMirror.getAnnotationType().asElement().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror
                        .getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("metadataKey")) {
                        TypeMirror typeMirror = (TypeMirror) entry.getValue().getValue();
                        return typeMirror.toString();
                    }
                }
            }
            annotationMirror.getAnnotationType();
        }
        throw new RuntimeException("'metadataKey' not found for " + element.asType());
    }

    private void writeConfigWithTypes(RoundEnvironment roundEnv, ConfigClassWriter configWriter, DocWriter docWriter) {
        Set<Element> serviceDiscoveries = collectElementsAnnotatedWith(roundEnv, ServiceDiscoveryType.class);
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeMirror serviceDiscoveryProviderType = typeUtils.erasure(
                elementUtils.getTypeElement(ServiceDiscoveryProvider.class.getName()).asType());

        Set<String> loaders = new HashSet<>();
        Set<String> types = new HashSet<>(); // to check if there are no two service discovery providers for the same type
        try {
            for (Element element : serviceDiscoveries) {
                if (element.getKind() != ElementKind.CLASS) {
                    throw new IllegalArgumentException(
                            "ServiceDiscoveryType annotation can only be used on the class level, found one on " + element);
                }
                TypeMirror elementType = elementUtils.getTypeElement(element.toString()).asType();
                if (!typeUtils.isAssignable(elementType, serviceDiscoveryProviderType)) {
                    throw new IllegalArgumentException(
                            "ServiceDiscoveryType should be used on ServiceDiscoveryProvider classes, found one on " + element);
                }

                ServiceDiscoveryAttribute[] attributes = EMPTY_SD_ATTRIBUTES;
                ServiceDiscoveryAttributes groupAnnoInstance = element.getAnnotation(ServiceDiscoveryAttributes.class);
                if (groupAnnoInstance != null) {
                    attributes = groupAnnoInstance.value();
                } else {
                    ServiceDiscoveryAttribute singleAnnotationInstance = element.getAnnotation(ServiceDiscoveryAttribute.class);
                    if (singleAnnotationInstance != null) {
                        attributes = new ServiceDiscoveryAttribute[] { singleAnnotationInstance };
                    }
                }
                validate(element.toString(), attributes);
                ServiceDiscoveryType serviceDiscoveryType = element.getAnnotation(ServiceDiscoveryType.class);
                // todo validate the ServiceDiscoveryProvider is parameterized by this class
                String type = serviceDiscoveryType.value();

                String configClassName = configWriter.createConfig(element, type, attributes);

                loaders.add(
                        configWriter.createServiceDiscoveryLoader(element, configClassName, type));
                if (!types.add(type)) {
                    throw new IllegalArgumentException("Multiple classes found for service discovery type: " + type);
                }

                docWriter.createAttributeTable(type, attributes);
            }
            configWriter.createServiceLoaderFile(ServiceDiscoveryLoader.class.getName(), loaders);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate configuration classes", e);
        }
    }

    private void writeLoadBalancerConfigs(RoundEnvironment roundEnv, ConfigClassWriter configWriter, DocWriter docWriter) {
        Set<Element> loadBalancers = collectElementsAnnotatedWith(roundEnv, LoadBalancerType.class);
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeMirror loadBalancerProviderType = typeUtils.erasure(
                elementUtils.getTypeElement(LoadBalancerProvider.class.getName()).asType());

        Set<String> loaders = new HashSet<>();
        Set<String> types = new HashSet<>();
        try {
            for (Element element : loadBalancers) {
                if (element.getKind() != ElementKind.CLASS) {
                    throw new IllegalArgumentException(
                            "LoadBalancerType annotation can only be used on the class level, found one on " + element);
                }
                TypeMirror elementType = elementUtils.getTypeElement(element.toString()).asType();
                if (!typeUtils.isAssignable(elementType, loadBalancerProviderType)) {
                    throw new IllegalArgumentException(
                            "LoadBalancerType should be used on LoadBalancerProvider classes, found one on " + element);
                }

                LoadBalancerAttribute[] attributes = EMPTY_LB_ATTRIBUTES;
                LoadBalancerAttributes groupAnnoInstance = element.getAnnotation(LoadBalancerAttributes.class);
                if (groupAnnoInstance != null) {
                    attributes = groupAnnoInstance.value();
                } else {
                    LoadBalancerAttribute singleAnnotationInstance = element.getAnnotation(LoadBalancerAttribute.class);
                    if (singleAnnotationInstance != null) {
                        attributes = new LoadBalancerAttribute[] { singleAnnotationInstance };
                    }
                }
                validate(element.toString(), attributes);
                LoadBalancerType loadBalancerType = element.getAnnotation(LoadBalancerType.class);
                // todo validate the LoadBalancerProvider is parameterized by this class
                String type = loadBalancerType.value();

                String configClassName = configWriter.createConfig(element, type, attributes);

                loaders.add(configWriter.createLoadBalancerLoader(element, configClassName, type));
                if (!types.add(type)) {
                    throw new IllegalArgumentException("Multiple classes found for load balancer type: " + type);
                }

                docWriter.createAttributeTable(type, attributes);
            }
            configWriter.createServiceLoaderFile(LoadBalancerLoader.class.getName(), loaders);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate configuration classes", e);
        }
    }

    private void validate(String className, LoadBalancerAttribute[] attributes) {
        Set<String> attributeNames = new HashSet<>();
        for (LoadBalancerAttribute attribute : attributes) {
            if (!attributeNames.add(attribute.name())) {
                throw new IllegalArgumentException("Attribute name " + attribute.name() + " duplicated on " + className);
            }
        }
    }

    private void validate(String className, ServiceDiscoveryAttribute[] attributes) {
        Set<String> attributeNames = new HashSet<>();
        for (ServiceDiscoveryAttribute attribute : attributes) {
            if (!attributeNames.add(attribute.name())) {
                throw new IllegalArgumentException("Attribute name " + attribute.name() + " duplicated on " + className);
            }
        }
    }

    private void validate(String className, ServiceRegistrarAttribute[] attributes) {
        Set<String> attributeNames = new HashSet<>();
        for (ServiceRegistrarAttribute attribute : attributes) {
            if (!attributeNames.add(attribute.name())) {
                throw new IllegalArgumentException("Attribute name " + attribute.name() + " duplicated on " + className);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    Set<Element> collectElementsAnnotatedWith(RoundEnvironment roundEnv, Class... annotations) {
        Set<Element> all = new HashSet<>();
        for (Class annotation : annotations) {
            all.addAll(roundEnv.getElementsAnnotatedWith(annotation));
        }
        return all;
    }
}
