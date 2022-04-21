package io.smallrye.stork.loadbalancer.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.smallrye.stork.api.config.ConfigWithType;
import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.NoAcceptableServiceInstanceFoundException;
import io.smallrye.stork.api.NoServiceInstanceFoundException;
import io.smallrye.stork.api.ServiceInstance;

class StickyLoadBalancerTest {
    public static final ConfigWithType DEFAULT_LB_CONFIG = new ConfigWithType() {
        @Override
        public String type() {
            return StickyLoadBalancerProvider.TYPE;
        }

        @Override
        public Map<String, String> parameters() {
            return Collections.emptyMap();
        }
    };
    public static final ConfigWithType WITH_FAILURE_BACKOFF_CONFIG = new ConfigWithType() {
        @Override
        public String type() {
            return StickyLoadBalancerProvider.TYPE;
        }

        @Override
        public Map<String, String> parameters() {
            return Map.of(StickyLoadBalancerProvider.FAILURE_BACKOFF_TIME, "60s");
        }
    };
    public static final long _70_s = Duration.ofSeconds(70).toNanos();
    public static final long _80_s = Duration.ofSeconds(80).toNanos();

    private final StickyLoadBalancerProviderLoader provider = new StickyLoadBalancerProviderLoader();

    @Test
    void shouldFailIfNoInstances() {
        var balancer = provider.createLoadBalancer(DEFAULT_LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        var balancerFailOnSingle = provider.createLoadBalancer(WITH_FAILURE_BACKOFF_CONFIG,
                () -> Uni.createFrom().item(Collections.emptyList()));
        assertThrows(NoServiceInstanceFoundException.class, () -> balancer.selectServiceInstance(Collections.emptyList()));
        assertThrows(NoServiceInstanceFoundException.class,
                () -> balancerFailOnSingle.selectServiceInstance(Collections.emptyList()));
    }

    @Test
    void shouldReturnConsistentlyIfOnlyOneServiceInstance() {
        LoadBalancer lb = provider.createLoadBalancer(DEFAULT_LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance = mockServiceInstance();
        assertSelectedFrom(lb, List.of(instance), instance);
        ServiceInstance selected;
        selected = lb.selectServiceInstance(List.of(instance));
        assertThat(selected.getId()).isEqualTo(instance.getId());

        selected.recordEnd(new Exception("induced failure"));
        selected = lb.selectServiceInstance(List.of(instance));
        assertThat(selected.getId()).isEqualTo(instance.getId());
    }

    @Test
    void shouldFailOnSingleFailingInstanceIfBackoffTimeSet() {
        LoadBalancer lb = provider.createLoadBalancer(WITH_FAILURE_BACKOFF_CONFIG,
                () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance = mockServiceInstance();
        var selected = assertSelectedFrom(lb, List.of(instance), instance);
        assertThat(selected.getId()).isEqualTo(instance.getId());

        selected.recordEnd(new Exception("induced failure"));
        assertThatThrownBy(() -> lb.selectServiceInstance(List.of(instance)))
                .isInstanceOf(NoAcceptableServiceInstanceFoundException.class);
        assertThatThrownBy(() -> lb.selectServiceInstance(List.of(instance)))
                .isInstanceOf(NoAcceptableServiceInstanceFoundException.class);
    }

    @Test
    void shouldContinueOnSingleInstanceIfBackoffTimePassed() {
        LoadBalancer lb = provider.createLoadBalancer(WITH_FAILURE_BACKOFF_CONFIG,
                () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance = mockServiceInstance();
        var selected = assertSelectedFrom(lb, List.of(instance), instance);

        ((StickyLoadBalancer) lb).recordEndAtTime(selected.getId(), new Exception("induced failure"),
                System.nanoTime() - _70_s);
        assertSelectedFrom(lb, List.of(instance), instance);
    }

    @Test
    void shouldSwitchToNextInstanceOnFailure() {
        LoadBalancer lb = provider.createLoadBalancer(DEFAULT_LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance1 = mockServiceInstance();
        ServiceInstance instance2 = mockServiceInstance();
        ServiceInstance instance3 = mockServiceInstance();

        List<ServiceInstance> list = List.of(instance1, instance2);
        assertSelectedFrom(lb, list, instance1);
        assertSelectedFrom(lb, list, instance1);
        assertSelectedFrom(lb, list, instance1);
        var lastSelected = assertSelectedFrom(lb, list, instance1);

        lastSelected.recordEnd(new Exception("failure"));
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);

        // check if adding an instance to the list doesn't change the result value:
        list = List.of(instance1, instance2, instance3);
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);
        lastSelected = assertSelectedFrom(lb, list, instance2);

        lastSelected.recordEnd(new Exception("exception thrown by service 2"));
        list = List.of(instance1, instance2, instance3);
        assertSelectedFrom(lb, list, instance3);
        assertSelectedFrom(lb, list, instance3);
        lastSelected = assertSelectedFrom(lb, list, instance3);

        // when 3 is marked as failed, 1 should be used once again as the one that failed the furthest in time
        lastSelected.recordEnd(new Exception("exception thrown by service 3"));
        assertSelectedFrom(lb, list, instance1);
        assertSelectedFrom(lb, list, instance1);
    }

    @Test
    void shouldFailOnMultipleInstancesIfAllFailedWithinBackoffTime() {
        LoadBalancer lb = provider.createLoadBalancer(WITH_FAILURE_BACKOFF_CONFIG,
                () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance1 = mockServiceInstance();
        ServiceInstance instance2 = mockServiceInstance();
        ServiceInstance instance3 = mockServiceInstance();

        List<ServiceInstance> list = List.of(instance1, instance2);
        assertSelectedFrom(lb, list, instance1);
        assertSelectedFrom(lb, list, instance1);
        assertSelectedFrom(lb, list, instance1);
        var lastSelected = assertSelectedFrom(lb, list, instance1);

        lastSelected.recordEnd(new Exception("failure"));
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);

        // check if adding an instance to the list doesn't change the result value:
        list = List.of(instance1, instance2, instance3);
        assertSelectedFrom(lb, list, instance2);
        assertSelectedFrom(lb, list, instance2);
        lastSelected = assertSelectedFrom(lb, list, instance2);

        lastSelected.recordEnd(new Exception("exception thrown by service 2"));
        assertSelectedFrom(lb, list, instance3);
        assertSelectedFrom(lb, list, instance3);
        lastSelected = assertSelectedFrom(lb, list, instance3);

        // when 3 is marked as failed, 1 should be used once again as the one that failed the furthest in time
        lastSelected.recordEnd(new Exception("exception thrown by service 3"));

        assertThatThrownBy(() -> lb.selectServiceInstance(List.of(instance1, instance2, instance3)))
                .isInstanceOf(NoAcceptableServiceInstanceFoundException.class);
    }

    /**
     * 1 fails now - 80s, 2 fails at now - 70s, 3 fails now
     *
     * we select next instance from (2,3), i.e. 1 is not available currently. To avoid memory leaks,
     * 1 should be removed from the collection of available service instances at this point (because it's an older failure
     * then the new selection made)
     */
    @Test
    @SuppressWarnings("deprecation")
    void shouldForgetAboutFailureIfHappenedBeforeNewlySelectedOne() {
        long now = System.nanoTime();

        LoadBalancer lb = provider.createLoadBalancer(DEFAULT_LB_CONFIG, () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance1 = mockServiceInstance();
        ServiceInstance instance2 = mockServiceInstance();
        ServiceInstance instance3 = mockServiceInstance();

        List<ServiceInstance> list = List.of(instance1, instance2, instance3);
        var selected = assertSelectedFrom(lb, list, instance1);
        ((StickyLoadBalancer) lb).recordEndAtTime(selected.getId(), new Exception("failure!"), now - _80_s);

        selected = assertSelectedFrom(lb, list, instance2);
        ((StickyLoadBalancer) lb).recordEndAtTime(selected.getId(), new Exception("failure!"), now - _70_s);

        selected = assertSelectedFrom(lb, list, instance3);
        ((StickyLoadBalancer) lb).recordEndAtTime(selected.getId(), new Exception("failure!"), now);

        assertSelectedFrom(lb, List.of(instance2, instance3), instance2);
        assertThat(((StickyLoadBalancer) lb).getFailedInstances()).hasSize(1); // only instance3 here
        assertThat(((StickyLoadBalancer) lb).getFailedInstances()).containsKey(instance3.getId());
    }

    @Test
    void shouldRejectFailedInstanceWithinBackoffTime() {
        LoadBalancer lb = provider.createLoadBalancer(WITH_FAILURE_BACKOFF_CONFIG,
                () -> Uni.createFrom().item(Collections.emptyList()));
        ServiceInstance instance1 = mockServiceInstance();
        ServiceInstance instance2 = mockServiceInstance();
        var selected = assertSelectedFrom(lb, List.of(instance1), instance1);
        selected.recordEnd(new Exception("induced failure"));
        assertThatThrownBy(() -> lb.selectServiceInstance(List.of(instance1)))
                .isInstanceOf(NoAcceptableServiceInstanceFoundException.class);

        assertSelectedFrom(lb, List.of(instance2), instance2);
        assertThatThrownBy(() -> lb.selectServiceInstance(List.of(instance1)))
                .isInstanceOf(NoAcceptableServiceInstanceFoundException.class);
    }

    private ServiceInstance assertSelectedFrom(LoadBalancer lb, List<ServiceInstance> list, ServiceInstance instance1) {
        ServiceInstance selected = lb.selectServiceInstance(list);
        assertThat(selected.getId()).isEqualTo(instance1.getId());
        return selected;
    }

    private static final AtomicLong serviceInstanceIds = new AtomicLong(1);

    private ServiceInstance mockServiceInstance() {
        ServiceInstance result = mock(ServiceInstance.class);
        long nextId = serviceInstanceIds.getAndIncrement();
        when(result.getId()).thenReturn(nextId);
        return result;
    }

    @Test
    public void testType() {
        assertThat(provider.type()).isEqualTo(StickyLoadBalancerProvider.TYPE);
    }
}
