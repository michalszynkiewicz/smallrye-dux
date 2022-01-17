package examples;

import java.time.Duration;
import java.util.List;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceInstance;

public class StorkServiceLookupExample {

    public static void main(String[] args) {
        Stork.initialize();
        Stork stork = Stork.getInstance();

        Service service = stork.getService("my-service");
        List<ServiceInstance> instances = service.getServiceInstances()
                .await().atMost(Duration.ofSeconds(5));

        // ...
        Stork.shutdown();
    }

}
