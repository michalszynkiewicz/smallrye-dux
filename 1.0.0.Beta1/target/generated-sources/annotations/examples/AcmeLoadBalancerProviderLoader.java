package examples;

import examples.AcmeLoadBalancerProviderConfiguration;
import examples.AcmeLoadBalancerProvider;
import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.config.LoadBalancerConfig;
import io.smallrye.stork.api.ServiceDiscovery;

/**
LoadBalancerLoader for examples.AcmeLoadBalancerProvider
 */
 public class AcmeLoadBalancerProviderLoader implements io.smallrye.stork.spi.internal.LoadBalancerLoader {
   private final examples.AcmeLoadBalancerProvider provider = new examples.AcmeLoadBalancerProvider();
   @Override
   public LoadBalancer createLoadBalancer(LoadBalancerConfig config, ServiceDiscovery serviceDiscovery) {
      examples.AcmeLoadBalancerProviderConfiguration typedConfig = new examples.AcmeLoadBalancerProviderConfiguration(config.parameters());
      return provider.createLoadBalancer(typedConfig, serviceDiscovery);
   }
   @Override
   public String type() {
      return "acme";
   }
}
