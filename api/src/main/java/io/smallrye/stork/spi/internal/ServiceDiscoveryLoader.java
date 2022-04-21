package io.smallrye.stork.spi.internal;

import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.spi.ElementWithType;
import io.smallrye.stork.spi.StorkInfrastructure;

/**
 * Used by stork internals to generate service loader for LoadBalancerProvider.
 */
public interface ServiceDiscoveryLoader extends ElementWithType {

    /**
     * Creates a new {@link ServiceDiscovery} instance.
     *
     * @param config the service discovery configuration, must not be {@code null}
     * @param serviceName the service name, must not be {@code null} or blank
     * @param serviceConfig the service configuration, must not be {@code null}
     * @param storkInfrastructure the stork infrastructure, must not be {@code null}
     * @return the new {@link ServiceDiscovery}
     */
    ServiceDiscovery createServiceDiscovery(ConfigWithType config, String serviceName,
            ServiceConfig serviceConfig, StorkInfrastructure storkInfrastructure);
}
