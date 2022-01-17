package examples;

import java.util.Map;

/**
 * Configuration for the {@code AcmeLoadBalancerProvider} LoadBalancer.
 */
 public class AcmeLoadBalancerProviderConfiguration {
   private final Map<String, String> parameters;
   public AcmeLoadBalancerProviderConfiguration(Map<String, String> params) {
      parameters = params;
   }
/**
 * Attribute that alters the behavior of the LoadBalancer
 */
   public String getMyAttribute() {
      return parameters.get("my-attribute");
   }
}
