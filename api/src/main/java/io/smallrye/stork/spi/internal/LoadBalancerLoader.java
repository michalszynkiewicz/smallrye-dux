package io.smallrye.stork.spi.internal;

import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.spi.ElementWithType;

/**
 * Used by stork internals to generate service loader for LoadBalancerProvider
 */
public interface LoadBalancerLoader extends ElementWithType {
    /**
     * Creates a load balancer instance.
     *
     * @param config the configuration, must not be {@code null}
     * @param serviceDiscovery the service discovery used for that service
     * @return the load balancer
     */
    LoadBalancer createLoadBalancer(ConfigWithType config, ServiceDiscovery serviceDiscovery);
}
