package io.smallrye.stork.api.config;

import java.util.Map;

/**
 * Load balancer configuration. Only used internally. The configuration is translated to an object
 * that provides parameters through getters
 */
public interface ConfigWithType {

    /**
     * Load balancer type, e.g. "round-robin".
     * A LoadBalancerProvider for the type has to be available
     *
     * @return load balancer type
     */
    String type();

    /**
     * Load Balancer parameters.
     * 
     * @return map of parameters of the load balancer
     */
    Map<String, String> parameters();
}
