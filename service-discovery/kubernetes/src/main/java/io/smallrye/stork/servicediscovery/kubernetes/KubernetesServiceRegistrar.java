package io.smallrye.stork.servicediscovery.kubernetes;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.Metadata;
import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.spi.StorkInfrastructure;

public class KubernetesServiceRegistrar implements ServiceRegistrar<KubernetesMetadataKey> {
    public KubernetesServiceRegistrar(KubernetesRegistrarConfiguration config, String serviceRegistrarName, StorkInfrastructure infrastructure) {


    }

    @Override
    public Uni<Void> registerServiceInstance(String serviceName, Metadata<KubernetesMetadataKey> metadata, String ipAddress) {
        return null;
    }
}
