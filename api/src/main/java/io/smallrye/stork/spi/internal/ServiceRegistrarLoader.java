package io.smallrye.stork.spi.internal;

import io.smallrye.stork.api.MetadataKey;
import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.spi.ElementWithType;
import io.smallrye.stork.spi.StorkInfrastructure;

/**
 * Used by stork internals to generate service loader for ServiceRegistrarProvider.
 */
public interface ServiceRegistrarLoader<MetadataKeyType extends Enum<MetadataKeyType> & MetadataKey> extends ElementWithType {

    /**
     * Creates a new {@link ServiceRegistrar} instance.
     *
     * @param config the service registrar configuration, must not be {@code null}
     * @param serviceName the service name, must not be {@code null} or blank
     * @param storkInfrastructure the stork infrastructure, must not be {@code null}
     * @return the new {@link ServiceRegistrar}
     */
    ServiceRegistrar<MetadataKeyType> createServiceRegistrar(ConfigWithType config, String serviceName,
            StorkInfrastructure storkInfrastructure);
}
