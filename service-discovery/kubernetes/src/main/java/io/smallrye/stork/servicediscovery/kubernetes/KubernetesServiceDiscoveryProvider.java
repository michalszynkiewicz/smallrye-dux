package io.smallrye.stork.servicediscovery.kubernetes;

import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.api.config.ServiceDiscoveryAttribute;
import io.smallrye.stork.api.config.ServiceDiscoveryType;
import io.smallrye.stork.spi.ServiceDiscoveryProvider;
import io.smallrye.stork.spi.StorkInfrastructure;
import io.vertx.core.Vertx;

/**
 * Service discovery provider for Kubernetes.
 */
@ServiceDiscoveryType("kubernetes")
@ServiceDiscoveryAttribute(name = "k8s-host", description = "The Kubernetes API host.")
@ServiceDiscoveryAttribute(name = "k8s-namespace", description = "The namespace of the service. Use all to discover all namespaces.")
@ServiceDiscoveryAttribute(name = "application", description = "The Kubernetes service name; if not defined Stork service name will be used.")
@ServiceDiscoveryAttribute(name = "refresh-period", description = "Service discovery cache refresh period.", defaultValue = "5M")
@ServiceDiscoveryAttribute(name = "secure", description = "Whether the connection with the service should be encrypted with TLS.")
public class KubernetesServiceDiscoveryProvider
        implements ServiceDiscoveryProvider<KubernetesConfiguration> {

    @Override
    public ServiceDiscovery createServiceDiscovery(KubernetesConfiguration config, String serviceName,
            ServiceConfig serviceConfig, StorkInfrastructure storkInfrastructure) {
        return new KubernetesServiceDiscovery(serviceName, config, storkInfrastructure.get(Vertx.class, Vertx::vertx));

    }
}
