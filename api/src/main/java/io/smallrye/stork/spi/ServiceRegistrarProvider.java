package io.smallrye.stork.spi;

import io.smallrye.stork.api.MetadataKey;
import io.smallrye.stork.api.ServiceRegistrar;

public interface ServiceRegistrarProvider<ConfigType, MetadataKeyType extends Enum<MetadataKeyType> & MetadataKey> {
    ServiceRegistrar<MetadataKeyType> createServiceRegistrar(ConfigType config, String serviceRegistrarName,
            StorkInfrastructure infrastructure);
}
