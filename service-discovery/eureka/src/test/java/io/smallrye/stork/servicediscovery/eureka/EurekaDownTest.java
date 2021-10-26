package io.smallrye.stork.servicediscovery.eureka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.smallrye.stork.Service;
import io.smallrye.stork.Stork;
import io.smallrye.stork.StorkTestUtils;
import io.smallrye.stork.test.TestConfigProvider;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

public class EurekaDownTest {

    private WebClient client;
    private Vertx vertx;

    @BeforeEach
    public void init() {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost(EurekaServer.EUREKA_HOST)
                .setDefaultPort(EurekaServer.EUREKA_PORT));
    }

    @AfterEach
    public void cleanup() {
        TestConfigProvider.clear();
        client.close();
        vertx.closeAndAwait();
    }

    private Stork configureAndGetStork(String serviceName) {
        Map<String, String> params = Map.of(
                "eureka-host", EurekaServer.EUREKA_HOST,
                "eureka-port", Integer.toString(EurekaServer.EUREKA_PORT),
                "refresh-period", "1S");
        TestConfigProvider.addServiceConfig(serviceName, null, "eureka", null, params);
        return StorkTestUtils.getNewStorkInstance();
    }

    @Test
    void testEurekaDown() {
        String serviceName = "my-service";
        Stork stork = configureAndGetStork(serviceName);
        Service service = stork.getService(serviceName);
        Assertions.assertNotNull(service);
        assertThatThrownBy(() -> service.getServiceInstances().await().atMost(Duration.ofSeconds(5)))
                .hasMessageContaining("ConnectException");
    }
}
