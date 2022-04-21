package io.smallrye.stork.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.NoSuchServiceDefinitionException;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.impl.RoundRobinLoadBalancer;
import io.smallrye.stork.impl.RoundRobinLoadBalancerProvider;
import io.smallrye.stork.spi.config.ConfigProvider;

@SuppressWarnings("unchecked")
public class StorkTest {

    private static final ConfigWithType FAKE_SERVICE_DISCOVERY_CONFIG = new ConfigWithType() {

        @Override
        public String type() {
            return "fake";
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };

    private static final ConfigWithType FAKE_SECURE_SERVICE_DISCOVERY_CONFIG = new ConfigWithType() {

        @Override
        public String type() {
            return "fake";
        }

        @Override
        public Map<String, String> parameters() {
            return Map.of("secure", "true");
        }
    };

    private static final ConfigWithType SERVICE_DISCOVERY_CONFIG_WITH_INVALID_PROVIDER = new ConfigWithType() {

        @Override
        public String type() {
            return "non-existent";
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };

    private static final ConfigWithType FAKE_LOAD_BALANCER_CONFIG = new ConfigWithType() {

        @Override
        public String type() {
            return "fake-selector";
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };

    private static final ConfigWithType LOAD_BALANCER_WITH_INVALID_PROVIDER = new ConfigWithType() {

        @Override
        public String type() {
            return "non-existent";
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };

    @BeforeEach
    public void init() {
        TestEnv.SPI_ROOT.mkdirs();
        AnchoredServiceDiscoveryProvider.services.clear();
        TestEnv.configurations.clear();
    }

    @AfterEach
    public void cleanup() throws IOException {
        Stork.shutdown();
        TestEnv.clearSPIs();
        AnchoredServiceDiscoveryProvider.services.clear();
        TestEnv.configurations.clear();
    }

    @Test
    void initWithoutConfigProvider() {
        Stork.initialize();
        Stork stork = Stork.getInstance();
        assertThat(stork.getServiceOptional("anything")).isEmpty();
    }

    @Test
    void initWithoutServiceDiscoveryOrLoadBalancer() {
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);
        Assertions.assertDoesNotThrow(() -> Stork.initialize());
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("missing").isEmpty());
        Assertions.assertThrows(NoSuchServiceDefinitionException.class, () -> stork.getService("missing"));
    }

    @Test
    public void initializationWithTwoConfigProviders() {
        TestEnv.install(ConfigProvider.class, ServiceAConfigProvider.class, ServiceBConfigProvider.class);
        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("a").isEmpty());

        Assertions.assertTrue(stork.getServiceOptional("missing").isEmpty());
        Assertions.assertTrue(stork.getServiceOptional("b").isPresent());
        Assertions.assertNotNull(stork.getService("b"));
    }

    @Test
    public void testServiceConfigWithoutServiceDiscovery() {
        Stork.initialize();
        assertThatThrownBy(() -> Stork.getInstance().defineIfAbsent("a", ServiceDefinition.of(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testServiceWithoutServiceDiscoveryType() {
        TestEnv.configurations.add(new FakeServiceConfig("a", new ConfigWithType() {
            @Override
            public String type() {
                return null;
            }

            @Override
            public Map<String, String> parameters() {
                return null;
            }
        }, null));
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Assertions.assertThrows(IllegalArgumentException.class, Stork::initialize);
    }

    @Test
    public void testServiceWithServiceDiscoveryButNoMatchingProvider() {
        TestEnv.configurations.add(new FakeServiceConfig("a", SERVICE_DISCOVERY_CONFIG_WITH_INVALID_PROVIDER, null));
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);
        Assertions.assertThrows(IllegalArgumentException.class, Stork::initialize);
    }

    @Test
    public void testWithLoadBalancerButNoMatchingProvider() {
        TestEnv.configurations
                .add(new FakeServiceConfig("a", FAKE_SERVICE_DISCOVERY_CONFIG, LOAD_BALANCER_WITH_INVALID_PROVIDER));
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);
        Assertions.assertThrows(IllegalArgumentException.class, Stork::initialize);
    }

    @Test
    public void testWithServiceDiscoveryAndASingleServiceInstance() {
        TestEnv.configurations.add(new FakeServiceConfig("a",
                FAKE_SERVICE_DISCOVERY_CONFIG, null));
        ServiceInstance instance = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance);
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("a").isPresent());
        Assertions.assertNotNull(stork.getService("a").getServiceDiscovery());
        Assertions.assertEquals(stork.getService("a").selectInstance().await().indefinitely(), instance);
        Assertions.assertNotNull(stork.getService("a").getLoadBalancer());
    }

    @Test
    public void testWithLegacySecureServiceDiscovery() {
        TestEnv.configurations.add(new FakeSecureServiceConfig("s",
                FAKE_SERVICE_DISCOVERY_CONFIG, null));
        ServiceInstance instance = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance);
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("s").isPresent());
        Assertions.assertNotNull(stork.getService("s").getServiceDiscovery());
        Assertions.assertNotNull(stork.getService("s").getLoadBalancer());
        Assertions.assertTrue(stork.getService("s").selectInstance().await().indefinitely().isSecure());
    }

    @Test
    public void testWithSecureServiceDiscovery() {
        TestEnv.configurations.add(new FakeServiceConfig("s",
                FAKE_SECURE_SERVICE_DISCOVERY_CONFIG, null));
        ServiceInstance instance = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance);
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("s").isPresent());
        Assertions.assertNotNull(stork.getService("s").getServiceDiscovery());
        Assertions.assertNotNull(stork.getService("s").getLoadBalancer());
        Assertions.assertTrue(stork.getService("s").selectInstance().await().indefinitely().isSecure());
    }

    @Test
    public void testWithServiceDiscoveryAndATwoServiceInstances() {
        TestEnv.configurations.add(new FakeServiceConfig("a",
                FAKE_SERVICE_DISCOVERY_CONFIG, null));
        ServiceInstance instance1 = mock(ServiceInstance.class);
        ServiceInstance instance2 = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance1);
        AnchoredServiceDiscoveryProvider.services.add(instance2);

        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("a").isPresent());
        Assertions.assertNotNull(stork.getService("a").getServiceDiscovery());
        Assertions.assertTrue(stork.getService("a").getInstances().await().indefinitely().contains(instance1));
        Assertions.assertTrue(stork.getService("a").getInstances().await().indefinitely().contains(instance2));
        Assertions.assertNotNull(stork.getService("a").getLoadBalancer());
    }

    @Test
    public void testWithLoadBalancer() {
        TestEnv.configurations.add(new FakeServiceConfig("a",
                FAKE_SERVICE_DISCOVERY_CONFIG, FAKE_LOAD_BALANCER_CONFIG));
        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);
        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertTrue(stork.getServiceOptional("a").isPresent());
        Assertions.assertNotNull(stork.getService("a").getLoadBalancer());
    }

    @Test
    public void testWithDefaultLoadBalancer() {
        ServiceInstance instance1 = mock(ServiceInstance.class);
        ServiceInstance instance2 = mock(ServiceInstance.class);
        ServiceInstance instance3 = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance1);
        AnchoredServiceDiscoveryProvider.services.add(instance2);
        AnchoredServiceDiscoveryProvider.services.add(instance3);

        TestEnv.configurations.add(new FakeServiceConfig("a",
                FAKE_SERVICE_DISCOVERY_CONFIG, null));

        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertEquals(instance1, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance2, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance3, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance1, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance2, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertTrue(stork.getServiceOptional("a").isPresent());
        Assertions.assertTrue(stork.getService("a").getLoadBalancer() instanceof RoundRobinLoadBalancer);
    }

    @Test
    public void testWithExplicitConfigurationOfTheRoundRobinLoadBalancer() {
        ServiceInstance instance1 = mock(ServiceInstance.class);
        ServiceInstance instance2 = mock(ServiceInstance.class);
        ServiceInstance instance3 = mock(ServiceInstance.class);
        AnchoredServiceDiscoveryProvider.services.add(instance1);
        AnchoredServiceDiscoveryProvider.services.add(instance2);
        AnchoredServiceDiscoveryProvider.services.add(instance3);

        TestEnv.configurations.add(new FakeServiceConfig("a",
                FAKE_SERVICE_DISCOVERY_CONFIG, new ConfigWithType() {
                    @Override
                    public String type() {
                        return RoundRobinLoadBalancerProvider.ROUND_ROBIN_TYPE;
                    }

                    @Override
                    public Map<String, String> parameters() {
                        return Collections.emptyMap();
                    }
                }));

        TestEnv.install(ConfigProvider.class, TestEnv.AnchoredConfigProvider.class);

        Stork.initialize();
        Stork stork = Stork.getInstance();
        Assertions.assertEquals(instance1, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance2, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance3, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance1, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertEquals(instance2, stork.getService("a").selectInstance().await().indefinitely());
        Assertions.assertTrue(stork.getServiceOptional("a").isPresent());
        Assertions.assertTrue(stork.getService("a").getLoadBalancer() instanceof RoundRobinLoadBalancer);
    }

    public static class ServiceAConfigProvider implements ConfigProvider {

        @Override
        public List<ServiceConfig> getConfigs() {
            ServiceConfig service = new FakeServiceConfig("a", FAKE_SERVICE_DISCOVERY_CONFIG, null);
            return List.of(service);
        }

        @Override
        public int priority() {
            return 5;
        }
    }

    public static class ServiceBConfigProvider implements ConfigProvider {

        @Override
        public List<ServiceConfig> getConfigs() {
            ServiceConfig service = new FakeServiceConfig("b", FAKE_SERVICE_DISCOVERY_CONFIG, null);
            return List.of(service);
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    private static class FakeServiceConfig implements ServiceConfig {

        private final String name;
        private final ConfigWithType lb;
        private final ConfigWithType sd;

        private FakeServiceConfig(String name, ConfigWithType sd, ConfigWithType lb) {
            this.name = name;
            this.lb = lb;
            this.sd = sd;
        }

        @Override
        public String serviceName() {
            return name;
        }

        @Override
        public ConfigWithType loadBalancer() {
            return lb;
        }

        @Override
        public ConfigWithType serviceDiscovery() {
            return sd;
        }

        @Override
        public boolean secure() {
            return false;
        }
    }

    private static class FakeSecureServiceConfig extends FakeServiceConfig {

        private FakeSecureServiceConfig(String name, ConfigWithType sd, ConfigWithType lb) {
            super(name, sd, lb);
        }

        @Override
        public boolean secure() {
            return true;
        }
    }

}
