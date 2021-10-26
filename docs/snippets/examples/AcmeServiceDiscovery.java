package examples;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.DefaultServiceInstance;
import io.smallrye.stork.ServiceDiscovery;
import io.smallrye.stork.ServiceInstance;
import io.smallrye.stork.spi.ServiceInstanceIds;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AcmeServiceDiscovery implements ServiceDiscovery {

    private final String host;
    private final int port;

    public AcmeServiceDiscovery(Map<String, String> configuration) {
        this.host = configuration.get("host");
        this.port = Integer.parseInt(configuration.get("port"));
    }

    @Override
    public Uni<List<ServiceInstance>> getServiceInstances() {
        // Proceed to the lookup...
        // Here, we just return a DefaultServiceInstance with the configured host and port
        DefaultServiceInstance instance =
                new DefaultServiceInstance(ServiceInstanceIds.next(), host, port);
        return Uni.createFrom().item(() -> Collections.singletonList(instance));
    }
}
