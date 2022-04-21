package io.smallrye.stork;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.NoSuchServiceDefinitionException;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.api.StorkServiceRegistry;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.impl.RoundRobinLoadBalancer;
import io.smallrye.stork.impl.RoundRobinLoadBalancerProvider;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.ElementWithType;
import io.smallrye.stork.spi.StorkInfrastructure;
import io.smallrye.stork.spi.config.ConfigProvider;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import io.smallrye.stork.spi.internal.LoadBalancerLoader;
import io.smallrye.stork.spi.internal.ServiceDiscoveryLoader;

/**
 * The entrypoint for SmallRye Stork.
 * <p>
 * Use `Stork.getInstance()` to get a hold of a configured instance, and retrieve `ServiceDiscovery` and/or `LoadBalancer`
 * from it.
 */
public final class Stork implements StorkServiceRegistry {
    // TODO replace all the exceptions here with dedicated ones?

    /**
     * The stork name.
     */
    public static final String STORK = "stork";

    private static final Logger LOGGER = LoggerFactory.getLogger(Stork.class);

    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private final StorkInfrastructure infrastructure;

    @Override
    public Service getService(String serviceName) {
        Service service = services.get(serviceName);
        if (service == null) {
            throw new NoSuchServiceDefinitionException(serviceName);
        }
        return service;
    }

    @Override
    public Optional<Service> getServiceOptional(String serviceName) {
        Service service = services.get(serviceName);
        return Optional.ofNullable(service);
    }

    @Override
    public Map<String, Service> getServices() {
        return Collections.unmodifiableMap(services);
    }

    @Override
    public Stork defineIfAbsent(String name, ServiceDefinition definition) {
        ParameterValidation.nonNull(name, "name");
        ParameterValidation.nonNull(definition, "definition");
        // The service definition has already been validated during its construction
        // (so the discovery is not null)

        ServiceConfig config = toServiceConfig(name, definition);
        Service service = createService(config);
        services.putIfAbsent(name, service);
        service.getServiceDiscovery().initialize(this);
        return this;
    }

    private ServiceConfig toServiceConfig(String name, ServiceDefinition definition) {
        return new SimpleServiceConfig.Builder().setServiceName(name)
                .setLoadBalancer(definition.getLoadBalancer())
                .setServiceDiscovery(definition.getServiceDiscovery())
                .build();
    }

    /**
     * Exposed for tests.
     * Not to be used in production code
     *
     * @param storkInfrastructure the infrastructure, must not be {@code null}
     */
    @Deprecated
    public Stork(StorkInfrastructure storkInfrastructure) {
        this.infrastructure = storkInfrastructure;
        Map<String, LoadBalancerLoader> loadBalancerProviders = getAll(LoadBalancerLoader.class);
        Map<String, ServiceDiscoveryLoader> serviceDiscoveryProviders = getAll(ServiceDiscoveryLoader.class);

        ServiceLoader<ConfigProvider> configs = ServiceLoader.load(ConfigProvider.class);
        Optional<ConfigProvider> highestPrioConfigProvider = configs.stream()
                .map(ServiceLoader.Provider::get)
                .max(Comparator.comparingInt(ConfigProvider::priority));

        ConfigProvider configProvider = highestPrioConfigProvider.orElse(null);
        if (configProvider != null) {
            for (ServiceConfig serviceConfig : configProvider.getConfigs()) {
                Service service = createService(loadBalancerProviders, serviceDiscoveryProviders, serviceConfig);

                services.put(serviceConfig.serviceName(), service);
            }
            for (Service service : services.values()) {
                service.getServiceDiscovery().initialize(this);
            }
        }

    }

    private Service createService(ServiceConfig serviceConfig) {
        return createService(getAll(LoadBalancerLoader.class), getAll(ServiceDiscoveryLoader.class), serviceConfig);
    }

    private Service createService(Map<String, LoadBalancerLoader> loadBalancerProviders,
            Map<String, ServiceDiscoveryLoader> serviceDiscoveryProviders,
            ServiceConfig serviceConfig) {
        var ConfigWithType = serviceConfig.serviceDiscovery();
        if (ConfigWithType == null) {
            throw new IllegalArgumentException(
                    "No service discovery defined for service " + serviceConfig.serviceName());
        }
        String serviceDiscoveryType = ConfigWithType.type();
        if (serviceDiscoveryType == null) {
            throw new IllegalArgumentException(
                    "Service discovery type not defined for service " + serviceConfig.serviceName());
        }

        final var serviceDiscoveryProvider = serviceDiscoveryProviders.get(serviceDiscoveryType);
        if (serviceDiscoveryProvider == null) {
            throw new IllegalArgumentException("ServiceDiscoveryProvider not found for type " + serviceDiscoveryType);
        }

        if (serviceConfig.secure()) {
            // Backward compatibility
            LOGGER.warn("The 'secure' attribute is deprecated, use the 'secure' service discovery attribute instead");
            // We do not know if we can add to the parameters, such create a new SimpleConfigWithType
            Map<String, String> newConfig = new HashMap<>(ConfigWithType.parameters());
            newConfig.put("secure", "true");
            ConfigWithType = new SimpleServiceConfig.SimpleConfigWithType(serviceDiscoveryType, newConfig);
        }

        final var serviceDiscovery = serviceDiscoveryProvider.createServiceDiscovery(ConfigWithType,
                serviceConfig.serviceName(), serviceConfig, infrastructure);

        final var loadBalancerConfig = serviceConfig.loadBalancer();
        final LoadBalancer loadBalancer;
        if (loadBalancerConfig == null) {
            // no load balancer, use round-robin
            LOGGER.debug("No load balancer configured for type {}, using {}", serviceDiscoveryType,
                    RoundRobinLoadBalancerProvider.ROUND_ROBIN_TYPE);
            loadBalancer = new RoundRobinLoadBalancer();
        } else {
            String loadBalancerType = loadBalancerConfig.type();
            final var loadBalancerProvider = loadBalancerProviders.get(loadBalancerType);
            if (loadBalancerProvider == null) {
                throw new IllegalArgumentException("No LoadBalancerProvider for type " + loadBalancerType);
            }

            loadBalancer = loadBalancerProvider.createLoadBalancer(loadBalancerConfig, serviceDiscovery);
        }

        return new Service(serviceConfig.serviceName(), loadBalancer, serviceDiscovery,
                loadBalancer.requiresStrictRecording());
    }

    private <T extends ElementWithType> Map<String, T> getAll(Class<T> providerClass) {
        ServiceLoader<T> providers = ServiceLoader.load(providerClass);
        return providers.stream().map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(ElementWithType::type, Function.identity()));
    }

    private static final AtomicReference<Stork> REFERENCE = new AtomicReference<>();

    /**
     * @return the stork instance.
     */
    public static Stork getInstance() {
        return REFERENCE.get();
    }

    /**
     * Closes the stork instance.
     */
    public static void shutdown() {
        REFERENCE.set(null);
    }

    /**
     * Initialize the stork instance using the given infrastructure.
     *
     * @param infrastructure the infrastructure, must not be {@code null}
     */
    public static void initialize(StorkInfrastructure infrastructure) {
        REFERENCE.compareAndSet(null, new Stork(infrastructure));
    }

    /**
     * Initialize the stork instance using the default infrastructure.
     */
    public static void initialize() {
        initialize(new DefaultStorkInfrastructure());
    }
}
