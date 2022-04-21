package io.smallrye.stork.spi.config;

import java.util.Collections;
import java.util.Map;

import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.api.config.ServiceConfig;

/**
 * Implementation of {@link ServiceConfig} storing the service name, service discovery config and load balancer config.
 * Instances should be created using {@link Builder}.
 */
public class SimpleServiceConfig implements ServiceConfig {

    private final String serviceName;

    private final ConfigWithType loadBalancerConfig;
    private final ConfigWithType ConfigWithType;

    private SimpleServiceConfig(String serviceName,
            ConfigWithType loadBalancerConfig,
            ConfigWithType ConfigWithType) {
        this.serviceName = serviceName;
        this.loadBalancerConfig = loadBalancerConfig;
        this.ConfigWithType = ConfigWithType;
    }

    @Override
    public String serviceName() {
        return serviceName;
    }

    @Override
    public ConfigWithType loadBalancer() {
        return loadBalancerConfig;
    }

    @Override
    public ConfigWithType serviceDiscovery() {
        return ConfigWithType;
    }

    /**
     * A {@link SimpleServiceConfig} builder.
     */
    public static class Builder {
        String serviceName;
        ConfigWithType loadBalancerConfig;
        ConfigWithType ConfigWithType;
        boolean secure;

        /**
         * Sets the load balancer config.
         *
         * @param loadBalancer the load balancer config
         * @return the current builder
         */
        public Builder setLoadBalancer(ConfigWithType loadBalancer) {
            loadBalancerConfig = loadBalancer;
            return this;
        }

        /**
         * Sets the service discovery config.
         *
         * @param serviceDiscovery the service discovery config
         * @return the current builder
         */
        public Builder setServiceDiscovery(ConfigWithType serviceDiscovery) {
            ConfigWithType = serviceDiscovery;
            return this;
        }

        /**
         * Sets the service name.
         *
         * @param serviceName the service name, must not be {@code null} or blank.
         * @return the current builder
         */
        public Builder setServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Sets to {@code true} to indicate that the service uses a <em>secure transport</em> (TLS).
         *
         * @param secure {@code true} to indicate that the service uses TLS.
         * @return the current builder
         */
        public Builder setSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Creates the {@link SimpleServiceConfig}
         *
         * @return the built config
         */
        public SimpleServiceConfig build() {
            return new SimpleServiceConfig(serviceName, loadBalancerConfig, ConfigWithType);
        }
    }

    /**
     * An implementation of {@link ConfigWithType} using an unmodifiable {@link Map} as backend to store
     * the configuration.
     */
    public static class SimpleLoadBalancerConfig implements ConfigWithType {

        private final String type;
        private final Map<String, String> parameters;

        /**
         * Creates a new SimpleLoadBalancerConfig.
         *
         * @param type the type
         * @param parameters the configuration map
         */
        public SimpleLoadBalancerConfig(String type, Map<String, String> parameters) {
            this.type = type;
            this.parameters = Collections.unmodifiableMap(parameters);
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Map<String, String> parameters() {
            return parameters;
        }
    }

    /**
     * An implementation of {@link ConfigWithType} using an unmodifiable {@link Map} as backend to store
     * the configuration.
     */
    public static class SimpleConfigWithType implements ConfigWithType {
        private final String type;
        private final Map<String, String> parameters;

        /**
         * Creates a new SimpleConfigWithType.
         *
         * @param type the type
         * @param parameters the configuration map
         */
        public SimpleConfigWithType(String type, Map<String, String> parameters) {
            this.type = type;
            this.parameters = Collections.unmodifiableMap(parameters);
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Map<String, String> parameters() {
            return parameters;
        }
    }

}
