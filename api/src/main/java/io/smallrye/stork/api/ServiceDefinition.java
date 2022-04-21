package io.smallrye.stork.api;

import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.stork.api.config.ConfigWithType;

/**
 * Define a service that will be managed by Stork.
 */
public class ServiceDefinition {
    private final ConfigWithType loadBalancer;
    private final ConfigWithType serviceDiscovery;

    private ServiceDefinition(ConfigWithType sd, ConfigWithType lb) {
        serviceDiscovery = ParameterValidation.nonNull(sd, "service discovery config");
        loadBalancer = lb;
    }

    /**
     * Creates a new {@link ServiceDefinition} using the given {@link ConfigWithType}.
     *
     * @param sd the service discovery config, must not be {@code null}
     * @return the created service definition
     */
    public static ServiceDefinition of(ConfigWithType sd) {
        return of(sd, null);
    }

    /**
     * Creates a new {@link ServiceDefinition} using the given {@link ConfigWithType} and {@link ConfigWithType}.
     *
     * @param sd the service discovery config, must not be {@code null}
     * @param lb the load balancer config, if {@code null}, round-robin is used.
     * @return the created service definition
     */
    public static ServiceDefinition of(ConfigWithType sd, ConfigWithType lb) {
        return new ServiceDefinition(sd, lb);
    }

    /**
     * @return the configured load balancer config.
     */
    public ConfigWithType getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * @return the configured service discovery config.
     */
    public ConfigWithType getServiceDiscovery() {
        return serviceDiscovery;
    }
}
