package io.smallrye.stork.servicediscovery.eureka;

import static io.smallrye.stork.servicediscovery.eureka.EurekaServer.registerApplicationInstance;
import static io.smallrye.stork.servicediscovery.eureka.EurekaServer.updateApplicationInstanceStatus;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.*;

import org.junit.jupiter.api.*;

import io.smallrye.stork.Service;
import io.smallrye.stork.ServiceInstance;
import io.smallrye.stork.Stork;
import io.smallrye.stork.StorkTestUtils;
import io.smallrye.stork.test.TestConfigProvider;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

public class EurekaDiscoveryTest {

    private static Vertx vertx;
    private WebClient client;

    @BeforeAll
    static void startEureka() {
        vertx = Vertx.vertx();
        EurekaServer.start();
    }

    @AfterAll
    static void stopEureka() {
        vertx.closeAndAwait();
        EurekaServer.stop();
    }

    @BeforeEach
    public void init() {
        client = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost(EurekaServer.EUREKA_HOST)
                .setDefaultPort(EurekaServer.EUREKA_PORT));
    }

    @AfterEach
    public void cleanup() {
        waitForCacheExpiration(); // Need to be sure our calls to Eureka won't be cached
        EurekaServer.unregisterAll(client);
        waitForCacheExpiration(); // Need to be sure that the test will not hit cached responses
        TestConfigProvider.clear();
        client.close();
    }

    @Test
    public void testWithoutApplicationInstancesThenOne() {
        String serviceName = "my-service";
        registerApplicationInstance(client, "another-service", "id1", "acme.com", 1234, null, -1, "UP");
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();

        registerApplicationInstance(client, serviceName, "id0", "com.example", 1111, null, -1, "STARTING");
        waitForCacheExpiration();

        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();

        updateApplicationInstanceStatus(client, serviceName, "id0", "UP");
        waitForCacheExpiration();

        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1).allSatisfy(instance -> {
            assertThat(instance.getHost()).isEqualTo("com.example");
            assertThat(instance.getPort()).isEqualTo(1111);
        });
    }

    @Test
    public void testWithTwoUpApplicationInstances() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "UP");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, null, -1, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(2)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1234);
                })
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });
    }

    @Test
    public void testWithTwoUpAndSecuredApplicationInstances() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, "secure.acme.com", 433, "UP");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, null, 8433, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName, "secure", "true");
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(2)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("secure.acme.com");
                    assertThat(instance.getPort()).isEqualTo(433);
                })
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(8433);
                });
    }

    @Test
    public void testWithTwoUpApplicationInstancesButOnlyOneUp() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "DOWN");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, null, -1, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .allSatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });

        updateApplicationInstanceStatus(client, "my-service", "id1", "UP");
        waitForCacheExpiration();
        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(2)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1234);
                })
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });
    }

    @Test
    public void testWithTwoOOSUpApplicationInstancesThenUp() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "OUT_OF_SERVICE");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, null, -1, "OUT_OF_SERVICE");
        registerApplicationInstance(client, "my-second-service", "id1", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();

        updateApplicationInstanceStatus(client, "my-service", "id1", "STARTING");
        updateApplicationInstanceStatus(client, "my-service", "id2", "STARTING");
        waitForCacheExpiration();

        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();

        updateApplicationInstanceStatus(client, "my-service", "id1", "UP");
        waitForCacheExpiration();

        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .allSatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1234);
                });

        updateApplicationInstanceStatus(client, "my-service", "id2", "UP");
        waitForCacheExpiration();
        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(2)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1234);
                })
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });
    }

    @Test
    public void testWithOneUpApplicationInstanceThenDown() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1234);
                });

        updateApplicationInstanceStatus(client, "my-service", "id1", "OUT_OF_SERVICE");
        waitForCacheExpiration();

        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();

        registerApplicationInstance(client, "my-service", "id2", "acme.com", 1235, null, -1, "UP");
        waitForCacheExpiration();
        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });

    }

    @Test
    public void testWithTwoUpApplicationInstancesBytOnlyOneSecure() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "UP");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, "ssl.acme.com", 433, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName, "secure", "true");
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("ssl.acme.com");
                    assertThat(instance.getPort()).isEqualTo(433);
                });
    }

    @Test
    public void testInstanceSelection() {
        registerApplicationInstance(client, "my-service", "id1", "acme.com", 1234, null, -1, "UP");
        registerApplicationInstance(client, "my-service", "id2", "acme2.com", 1235, "ssl.acme.com", 433, "UP");
        registerApplicationInstance(client, "my-second-service", "second", "acme.com", 1236, null, -1, "UP");

        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName, "instance", "id2");
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        List<ServiceInstance> instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).hasSize(1)
                .anySatisfy(instance -> {
                    assertThat(instance.getHost()).isEqualTo("acme2.com");
                    assertThat(instance.getPort()).isEqualTo(1235);
                });

        stork = configureAndGetStork(serviceName, "instance", "missing");
        service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        instances = service.getServiceInstances().await().atMost(Duration.ofSeconds(5));
        assertThat(instances).isEmpty();
    }

    private Stork configureAndGetStork(String serviceName) {
        Map<String, String> params = Map.of(
                "eureka-host", EurekaServer.EUREKA_HOST,
                "eureka-port", Integer.toString(EurekaServer.EUREKA_PORT),
                "refresh-period", "1S");
        TestConfigProvider.addServiceConfig(serviceName, null, "eureka", null, params);
        return StorkTestUtils.getNewStorkInstance();
    }

    private Stork configureAndGetStork(String serviceName, String... extra) {
        Map<String, String> params = new HashMap<>(Map.of(
                "eureka-host", EurekaServer.EUREKA_HOST,
                "eureka-port", Integer.toString(EurekaServer.EUREKA_PORT),
                "refresh-period", "1S"));

        if (extra.length > 0) {
            Iterator<String> iterator = Arrays.stream(extra).iterator();
            while (iterator.hasNext()) {
                params.put(iterator.next(), iterator.next());
            }
        }

        TestConfigProvider.addServiceConfig(serviceName, null, "eureka", null, params);
        return StorkTestUtils.getNewStorkInstance();
    }

    private void waitForCacheExpiration() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}