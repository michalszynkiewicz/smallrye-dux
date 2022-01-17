package examples;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.impl.DefaultServiceInstance;
import io.smallrye.stork.spi.ServiceInstanceIds;

public class AcmeServiceDiscovery implements ServiceDiscovery {

    private final String host;
    private final int port;

    public AcmeServiceDiscovery(AcmeServiceDiscoveryProviderConfiguration configuration) {
        this.host = configuration.getHost();
        this.port = Integer.parseInt(configuration.getPort());
    }

    @Override
    public Uni<List<ServiceInstance>> getServiceInstances() {
        // Proceed to the lookup...
        // Here, we just return a DefaultServiceInstance with the configured host and port
        // The last parameter specifies whether the communication with the instance should happen over a secure connection
        DefaultServiceInstance instance =
                new DefaultServiceInstance(ServiceInstanceIds.next(), host, port, false);
        return Uni.createFrom().item(() -> Collections.singletonList(instance));
    }
}
