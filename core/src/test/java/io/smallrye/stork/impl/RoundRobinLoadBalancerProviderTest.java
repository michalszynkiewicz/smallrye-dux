package io.smallrye.stork.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.NoServiceInstanceFoundException;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.api.config.ConfigWithType;

class RoundRobinLoadBalancerProviderTest {

    public static final ConfigWithType LB_CONFIG = new ConfigWithType() {
        @Override
        public String type() {
            return RoundRobinLoadBalancerProvider.ROUND_ROBIN_TYPE;
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };
    private final RoundRobinLoadBalancerProviderLoader provider = new RoundRobinLoadBalancerProviderLoader();
    private LoadBalancer balancer;

    @BeforeEach
    public void init() {
        balancer = provider.createLoadBalancer(LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
    }

    @Test
    public void testWithoutServiceInstances() {
        assertThrows(NoServiceInstanceFoundException.class, () -> balancer.selectServiceInstance(Collections.emptyList()));
    }

    @Test
    public void testWithASingleServiceInstance() {
        LoadBalancer lb = provider.createLoadBalancer(LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance = mock(ServiceInstance.class);
        // 3 selection in a row -> always the same instance
        ServiceInstance selected = lb.selectServiceInstance(List.of(instance));
        assertThat(selected).isEqualTo(instance);
        selected = lb.selectServiceInstance(List.of(instance));
        assertThat(selected).isEqualTo(instance);
        selected = lb.selectServiceInstance(List.of(instance));
        assertThat(selected).isEqualTo(instance);
    }

    @Test
    public void testWithMultiServiceInstances() {
        LoadBalancer lb = provider.createLoadBalancer(LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance1 = mock(ServiceInstance.class);
        ServiceInstance instance2 = mock(ServiceInstance.class);
        ServiceInstance instance3 = mock(ServiceInstance.class);
        when(instance1.getId()).thenReturn(1L);
        when(instance2.getId()).thenReturn(2L);
        when(instance3.getId()).thenReturn(3L);
        List<ServiceInstance> list = List.of(instance1, instance2);
        ServiceInstance selected = lb.selectServiceInstance(list);
        assertThat(selected).isEqualTo(instance1);
        selected = lb.selectServiceInstance(list);
        assertThat(selected).isEqualTo(instance2);
        selected = lb.selectServiceInstance(list);
        assertThat(selected).isEqualTo(instance1);
        selected = lb.selectServiceInstance(list);
        assertThat(selected).isEqualTo(instance2);
        selected = lb.selectServiceInstance(Collections.singletonList(instance2));
        assertThat(selected).isEqualTo(instance2);
        selected = lb.selectServiceInstance(Collections.singletonList(instance1));
        assertThat(selected).isEqualTo(instance1);
        selected = lb.selectServiceInstance(List.of(instance1, instance2, instance3));
        assertThat(selected).isEqualTo(instance1);
        selected = lb.selectServiceInstance(List.of(instance1, instance2, instance3));
        assertThat(selected).isEqualTo(instance2);
        selected = lb.selectServiceInstance(List.of(instance1, instance2, instance3));
        assertThat(selected).isEqualTo(instance3);
        selected = lb.selectServiceInstance(List.of(instance1, instance2, instance3));
        assertThat(selected).isEqualTo(instance1);
    }

    @Test
    public void testType() {
        assertThat(provider.type()).isEqualTo(RoundRobinLoadBalancerProvider.ROUND_ROBIN_TYPE);
    }

}
