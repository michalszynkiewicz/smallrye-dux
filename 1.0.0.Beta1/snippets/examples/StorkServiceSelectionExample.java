package examples;

import java.time.Duration;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceInstance;

public class StorkServiceSelectionExample {

    public static void main(String[] args) {
        Stork.initialize();
        Stork stork = Stork.getInstance();

        Service service = stork.getService("my-service");
        ServiceInstance instance = service.selectServiceInstance()
                .await().atMost(Duration.ofSeconds(5));

        System.out.println(instance.getHost() + ":" + instance.getPort());

        // ...
        Stork.shutdown();
    }

}
