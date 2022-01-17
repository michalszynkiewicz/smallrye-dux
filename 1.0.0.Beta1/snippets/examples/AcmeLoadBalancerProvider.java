package examples;

import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.LoadBalancerAttribute;
import io.smallrye.stork.api.config.LoadBalancerType;
import io.smallrye.stork.spi.LoadBalancerProvider;

@LoadBalancerType("acme")
@LoadBalancerAttribute(name = "my-attribute",
        description = "Attribute that alters the behavior of the LoadBalancer")
public class AcmeLoadBalancerProvider implements
        LoadBalancerProvider<AcmeLoadBalancerProviderConfiguration> {

    @Override
    public LoadBalancer createLoadBalancer(AcmeLoadBalancerProviderConfiguration config,
                                           ServiceDiscovery serviceDiscovery) {
        return new AcmeLoadBalancer(config);
    }
}
