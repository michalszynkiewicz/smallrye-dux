package io.smallrye.stork.servicediscovery.kubernetes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.api.config.ServiceRegistrarAttribute;
import io.smallrye.stork.api.config.ServiceRegistrarType;
import io.smallrye.stork.spi.ServiceRegistrarProvider;
import io.smallrye.stork.spi.StorkInfrastructure;

@ServiceRegistrarAttribute(name = "k8s-host", description = "The Kubernetes API host.")
@ServiceRegistrarType(value = "kubernetes", metadataKey = KubernetesMetadataKey.class)
public class KubernetesServiceRegistrarProvider
        implements ServiceRegistrarProvider<KubernetesRegistrarConfiguration, KubernetesMetadataKey> {

    private static final Logger log = LoggerFactory.getLogger(KubernetesServiceRegistrarProvider.class);

    @Override
    public ServiceRegistrar<KubernetesMetadataKey> createServiceRegistrar(KubernetesRegistrarConfiguration config,
            String serviceRegistrarName, StorkInfrastructure infrastructure) {
        return new KubernetesServiceRegistrar(config, serviceRegistrarName, infrastructure);
    }

    //    @Override
    //    public void init(StorkInfrastructure infrastructure) {
    //        vertx = infrastructure.get(Vertx.class, Vertx::vertx);
    //    }
    //
    //    @Override
    //    public Uni<Void> registerServiceInstance(String serviceName, Metadata<KubernetesMetadataKey> metadata, String ipAddress) {
    //        return Uni.createFrom().emitter(em -> vertx.executeBlocking(future -> {
    //            registerKubernetesService(serviceName, metadata, ipAddress);
    //            future.complete();
    //        }, result -> {
    //            if (result.succeeded()) {
    //                log.error("Instances of service {} has been resgistered ", serviceName);
    //            } else {
    //                log.error("Unable to register instances of service {}", serviceName,
    //                        result.cause());
    //            }
    //        }));
    //    }
    //
    //    private void registerKubernetesService(String serviceName, Metadata<KubernetesMetadataKey> metadata, String ipAddress) {
    //        Config base = Config.autoConfigure(null);
    //        String masterUrl = config.getK8sHost() == null ? base.getMasterUrl() : config.getK8sHost();
    //        this.application = config.getApplication() == null ? serviceName : config.getApplication();
    //        this.namespace = config.getK8sNamespace() == null ? base.getNamespace() : config.getK8sNamespace();
    //
    //        allNamespaces = namespace != null && namespace.equalsIgnoreCase("all");
    //
    //        if (namespace == null) {
    //            throw new IllegalArgumentException("Namespace is not configured for service '" + serviceName
    //                    + "'. Please provide a namespace. Use 'all' to discover services in all namespaces");
    //        }
    //
    //        Config k8sConfig = new ConfigBuilder(base)
    //                .withMasterUrl(masterUrl)
    //                .withNamespace(namespace).build();
    //        this.client = new DefaultKubernetesClient(k8sConfig);
    //
    //        String namespace = (String) metadata.getMetadata().get(KubernetesMetadataKey.META_K8S_SERVICE_ID);
    //        //        defaultNamespace = base.getNamespace() : config.getK8sNamespace();
    //        Map<String, String> serviceLabels = new HashMap<>();
    //        serviceLabels.put("app.kubernetes.io/name", "svc");
    //        serviceLabels.put("app.kubernetes.io/version", "1.0");
    //
    //        registerBackendPods(serviceName, namespace, serviceLabels, ipAddress);
    //
    //        ObjectReference targetRef = new ObjectReference(null, null, "Pod",
    //                serviceName + "-" + ipAsSuffix(ipAddress), namespace, null, UUID.randomUUID().toString());
    //        EndpointAddress endpointAddress = new EndpointAddressBuilder().withIp(ipAddress).withTargetRef(targetRef)
    //                .build();
    //        Endpoints endpoint = new EndpointsBuilder()
    //                .withNewMetadata().withName(serviceName).withLabels(serviceLabels).endMetadata()
    //                .addToSubsets(new EndpointSubsetBuilder().withAddresses(endpointAddress)
    //                        .addToPorts(new EndpointPortBuilder().withPort(8080).build())
    //                        .build())
    //                .build();
    //
    //        client.endpoints().inNamespace(namespace).withName(serviceName).create(endpoint);
    //
    //    }
    //
    //    private void registerBackendPods(String name, String namespace, Map<String, String> labels, String ipAdress) {
    //        Map<String, String> podLabels = new HashMap<>(labels);
    //        podLabels.put("ui", "ui-" + ipAsSuffix(ipAdress));
    //        Pod backendPod = new PodBuilder().withNewMetadata().withName(name + "-" + ipAsSuffix(ipAdress))
    //                .withLabels(podLabels)
    //                .endMetadata()
    //                .build();
    //        client.pods().inNamespace(namespace).create(backendPod);
    //    }
    //
    //    private String ipAsSuffix(String ipAddress) {
    //        return ipAddress.replace(".", "");
    //    }

}
