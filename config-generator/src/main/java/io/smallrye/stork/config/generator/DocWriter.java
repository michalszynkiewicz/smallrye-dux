package io.smallrye.stork.config.generator;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.smallrye.stork.api.config.Constants;
import io.smallrye.stork.api.config.LoadBalancerAttribute;
import io.smallrye.stork.api.config.ServiceDiscoveryAttribute;
import io.smallrye.stork.api.config.ServiceRegistrarAttribute;

public class DocWriter {
    private static final String STORK_DOCS_DIR = "META-INF/stork-docs/";

    private final ProcessingEnvironment environment;

    public DocWriter(ProcessingEnvironment environment) {
        this.environment = environment;
    }

    public void createAttributeTable(String serviceRegistrarType, ServiceRegistrarAttribute[] attributes) throws IOException {
        String attributeTableFile = serviceRegistrarType + "-sr-attributes.txt";
        FileObject loaderFile = createResourceFile(attributeTableFile);
        try (PrintWriter out = new PrintWriter(loaderFile.openWriter())) {
            out.println(" | Attribute            | Mandatory  | Default Value      | Description  |");
            out.println(" |----------------------|------------|--------------------|--------------|");
            for (ServiceRegistrarAttribute attribute : attributes) {
                out.println(String.format("| `%s` | %s | %s | %s |",
                        attribute.name(),
                        attribute.required() ? "Yes" : "No",
                        Constants.DEFAULT_VALUE.equals(attribute.defaultValue()) ? "" : '`' + attribute.defaultValue() + '`',
                        attribute.description()));
            }
        }
    }

    public void createAttributeTable(String serviceDiscoveryType, ServiceDiscoveryAttribute[] attributes) throws IOException {
        String attributeTableFile = serviceDiscoveryType + "-sd-attributes.txt";
        FileObject loaderFile = createResourceFile(attributeTableFile);
        try (PrintWriter out = new PrintWriter(loaderFile.openWriter())) {
            out.println(" | Attribute            | Mandatory  | Default Value      | Description  |");
            out.println(" |----------------------|------------|--------------------|--------------|");
            for (ServiceDiscoveryAttribute attribute : attributes) {
                out.println(String.format("| `%s` | %s | %s | %s |",
                        attribute.name(),
                        attribute.required() ? "Yes" : "No",
                        Constants.DEFAULT_VALUE.equals(attribute.defaultValue()) ? "" : '`' + attribute.defaultValue() + '`',
                        attribute.description()));
            }
        }
    }

    public void createAttributeTable(String loadBalancerType, LoadBalancerAttribute[] attributes) throws IOException {
        String attributeTableFile = loadBalancerType + "-lb-attributes.txt";
        FileObject loaderFile = createResourceFile(attributeTableFile);

        try (PrintWriter out = new PrintWriter(loaderFile.openWriter())) {
            out.println(" | Attribute            | Mandatory  | Default Value      | Description  |");
            out.println(" |----------------------|------------|--------------------|--------------|");
            for (LoadBalancerAttribute attribute : attributes) {
                out.println(String.format("| `%s` | %s | %s | %s |",
                        attribute.name(),
                        attribute.required() ? "Yes" : "No",
                        Constants.DEFAULT_VALUE.equals(attribute.defaultValue()) ? "" : '`' + attribute.defaultValue() + '`',
                        attribute.description()));
            }
        }
    }

    private FileObject createResourceFile(String fileName) throws IOException {
        FileObject loaderFile = environment.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                STORK_DOCS_DIR + fileName);
        loaderFile.delete();
        return loaderFile;
    }
}
