package examples;

import java.util.Map;

/**
 * Configuration for the {@code AcmeServiceDiscoveryProvider} ServiceDiscovery.
 */
 public class AcmeServiceDiscoveryProviderConfiguration {
   private final Map<String, String> parameters;
   public AcmeServiceDiscoveryProviderConfiguration(Map<String, String> params) {
      parameters = params;
   }
/**
 * Host name of the service discovery server.
 */
   public String getHost() {
      return parameters.get("host");
   }
/**
 * Hort of the service discovery server.
 */
   public String getPort() {
      return parameters.get("port");
   }
}
