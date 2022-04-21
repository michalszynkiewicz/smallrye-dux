package io.smallrye.stork.microprofile;

import static io.smallrye.stork.Stork.STORK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.spi.config.ConfigProvider;
import io.smallrye.stork.spi.config.SimpleServiceConfig;

/**
 * Implementation of {@link ConfigProvider} using MicroProfile Config.
 */
public class MicroProfileConfigProvider implements ConfigProvider {

    private static final Logger log = Logger.getLogger(MicroProfileConfigProvider.class);

    private static final String CONFIG_PROPERTY_PART_EXPRESSION = "\".*\"|[^.]+";
    private static final Pattern CONFIG_PROP_PART = Pattern.compile(CONFIG_PROPERTY_PART_EXPRESSION);

    /**
     * The load-balancer segment used in the configuration key.
     */
    public static final String LOAD_BALANCER = "load-balancer";
    /**
     * The load balancer type configuration key.
     */
    public static final String LOAD_BALANCER_EMBEDDED = "load-balancer.type";

    /**
     * The service-discovery segment used in the configuration key.
     */
    public static final String SERVICE_DISCOVERY = "service-discovery";
    /**
     * The service discovery type configuration key.
     */
    public static final String SERVICE_DISCOVERY_EMBEDDED = "service-discovery.type";

    private final List<ServiceConfig> serviceConfigs = new ArrayList<>();

    /**
     * Creates a new instance of MicroProfileConfigProvider.
     */
    public MicroProfileConfigProvider() {
        Config config = org.eclipse.microprofile.config.ConfigProvider.getConfig();

        Map<String, Map<String, String>> propertiesByServiceName = new HashMap<>();

        for (String propertyName : config.getPropertyNames()) {

            Matcher matcher = CONFIG_PROP_PART.matcher(propertyName);

            if (!matcher.find() || !STORK.equals(matcher.group())) {
                continue;
            }

            // all properties are now of form
            // stork.<service-name>.(load-balancer|service-discovery)...
            // or stork."<service-name>".(load-balancer|service-discovery)...
            if (!matcher.find()) {
                log.warn("Potentially invalid property for SmallRye Stork: " + propertyName);
            }
            String serviceName = unwrapFromQuotes(matcher.group());

            int serviceNameEndIdx = matcher.end();

            if (!matcher.find()) {
                log.warn("Potentially invalid property for SmallRye Stork: " + propertyName);
            }

            // serviceName can be in double quotes
            Map<String, String> serviceProperties = propertiesByServiceName.computeIfAbsent(serviceName,
                    ignored -> new HashMap<>());

            String serviceProperty = servicePropertyKey(propertyName.substring(serviceNameEndIdx));
            serviceProperties.put(serviceProperty,
                    config.getValue(propertyName, String.class));
        }

        for (Map.Entry<String, Map<String, String>> serviceEntry : propertiesByServiceName.entrySet()) {
            SimpleServiceConfig.Builder builder = new SimpleServiceConfig.Builder();

            Map<String, String> properties = serviceEntry.getValue();

            String serviceName = serviceEntry.getKey();

            String loadBalancerType = properties.get(LOAD_BALANCER);
            if (loadBalancerType == null) {
                loadBalancerType = properties.get(LOAD_BALANCER_EMBEDDED);
            }
            builder.setServiceName(serviceName);
            if (loadBalancerType != null) {
                SimpleServiceConfig.SimpleLoadBalancerConfig loadBalancerConfig = new SimpleServiceConfig.SimpleLoadBalancerConfig(
                        loadBalancerType, propertiesForPrefix(LOAD_BALANCER, properties));

                builder = builder.setLoadBalancer(loadBalancerConfig);
            }

            String serviceDiscoveryType = properties.get(SERVICE_DISCOVERY);
            // for yaml it is more convenient to have stork.my-service.service-discovery.type, so let's support it too:
            if (serviceDiscoveryType == null) {
                serviceDiscoveryType = properties.get(SERVICE_DISCOVERY_EMBEDDED);
            }
            if (serviceDiscoveryType != null) {
                SimpleServiceConfig.SimpleConfigWithType ConfigWithType = new SimpleServiceConfig.SimpleConfigWithType(
                        serviceDiscoveryType, propertiesForPrefix(SERVICE_DISCOVERY, properties));

                builder = builder.setServiceDiscovery(ConfigWithType);
            }

            serviceConfigs.add(builder.build());
        }
    }

    private String unwrapFromQuotes(String text) {
        if (text.length() < 2) {
            return text;
        }
        if (text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        } else {
            return text;
        }
    }

    private String servicePropertyKey(String text) {
        if (!text.isEmpty() && text.charAt(0) == '.') {
            return text.substring(1);
        }
        return text;
    }

    private Map<String, String> propertiesForPrefix(String prefix, Map<String, String> original) {
        prefix = prefix.endsWith(".") ? prefix : prefix + ".";
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : original.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.startsWith(prefix)) {
                String nameWithoutPrefix = propertyName.substring(prefix.length());
                result.put(nameWithoutPrefix, entry.getValue());
            }
        }

        return result;
    }

    @Override
    public List<ServiceConfig> getConfigs() {
        return serviceConfigs;
    }

    @Override
    public int priority() {
        return 100;
    }

}
