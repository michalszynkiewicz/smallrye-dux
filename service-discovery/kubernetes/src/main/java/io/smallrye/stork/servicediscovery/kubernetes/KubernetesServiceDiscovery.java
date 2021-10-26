package io.smallrye.stork.servicediscovery.kubernetes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.CachingServiceDiscovery;
import io.smallrye.stork.DefaultServiceInstance;
import io.smallrye.stork.ServiceInstance;
import io.smallrye.stork.config.ServiceDiscoveryConfig;
import io.smallrye.stork.spi.ServiceInstanceIds;
import io.smallrye.stork.spi.ServiceInstanceUtils;
import io.vertx.core.Vertx;

public class KubernetesServiceDiscovery extends CachingServiceDiscovery {

    public static final String METADATA_NAME = "metadata.name";
    private final KubernetesClient client;
    private final String serviceName;
    private final boolean allNamespaces;
    private final String namespace;
    private final Vertx vertx;

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesServiceDiscovery.class);

    public KubernetesServiceDiscovery(String serviceName, ServiceDiscoveryConfig config, Vertx vertx) {
        super(config);
        Config base = Config.autoConfigure(null);
        this.serviceName = serviceName;
        Map<String, String> parameters = config.parameters();
        String masterUrl = parameters != null ? parameters.get("k8s-host") : base.getMasterUrl();
        if (masterUrl == null) {
            masterUrl = base.getMasterUrl();
        }
        namespace = parameters != null ? parameters.get("k8s-namespace") : base.getNamespace();
        allNamespaces = namespace != null && namespace.equalsIgnoreCase("all");

        Config properties = new ConfigBuilder(base)
                .withMasterUrl(masterUrl)
                .withNamespace(namespace).build();
        client = new DefaultKubernetesClient(properties);
        this.vertx = vertx;
    }

    @Override
    public Uni<List<ServiceInstance>> fetchNewServiceInstances(List<ServiceInstance> previousInstances) {
        Uni<List<Endpoints>> endpointsUni = Uni.createFrom().emitter(
                emitter -> {
                    vertx.executeBlocking(future -> {
                        List<Endpoints> endpoints = new ArrayList<>();
                        if (allNamespaces) {
                            endpoints.addAll(client.endpoints().inAnyNamespace().withField(METADATA_NAME, serviceName).list()
                                    .getItems());
                        } else {
                            endpoints.addAll(
                                    client.endpoints().inNamespace(namespace).withField(METADATA_NAME, serviceName).list()
                                            .getItems());
                        }
                        future.complete(endpoints);
                    }, result -> {
                        if (result.succeeded()) {
                            @SuppressWarnings("unchecked")
                            List<Endpoints> endpoints = (List<Endpoints>) result.result();
                            emitter.complete(endpoints);
                        } else {
                            LOGGER.error("Unable to retrieve the endpoint from the {} service", serviceName, result.cause());
                            emitter.fail(result.cause());
                        }
                    });
                });
        return endpointsUni.onItem().transform(endpoints -> toStorkServiceInstances(endpoints, previousInstances));
    }

    private List<ServiceInstance> toStorkServiceInstances(List<Endpoints> endpointList, List<ServiceInstance> previousInstances) {
        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (Endpoints endPoints : endpointList) {
            for (EndpointSubset subset : endPoints.getSubsets()) {
                for (EndpointAddress endpointAddress : subset.getAddresses()) {
                    String hostname = endpointAddress.getIp();
                    if (hostname == null) { // should we take the hostName?
                        hostname = endpointAddress.getHostname();
                    }
                    List<EndpointPort> endpointPorts = subset.getPorts();
                    Integer port = 0;
                    if (endpointPorts.size() == 1) {
                        port = endpointPorts.get(0).getPort();
                    }

                    ServiceInstance matching = ServiceInstanceUtils.findMatching(previousInstances, hostname, port);

                    if (matching != null) {
                        serviceInstances.add(matching);
                    } else {
                        serviceInstances.add(new DefaultServiceInstance(ServiceInstanceIds.next(), hostname, port));
                    }
                }
            }
        }

        return serviceInstances;
    }
}
