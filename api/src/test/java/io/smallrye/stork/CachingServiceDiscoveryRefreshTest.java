package io.smallrye.stork;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

class CachingServiceDiscoveryRefreshTest {

    private static final Vertx vertx = Vertx.vertx();

    @RepeatedTest(4)
    @Timeout(20)
    void shouldTriggerSupplierOnceAndCompleteAllConsumersWhenDone() throws InterruptedException {
        int amountOfGets = 300;

        final AtomicReference<CachingServiceDiscovery.Refresh> refresh = new AtomicReference<>(null);

        List<Uni<List<ServiceInstance>>> results = Collections.synchronizedList(new ArrayList<>());

        AtomicInteger refreshCount = new AtomicInteger(0);
        CountDownLatch awaitFinish = new CountDownLatch(amountOfGets);

        ExecutorService executor = Executors.newFixedThreadPool(amountOfGets);

        for (int i = 0; i < amountOfGets; i++) {
            executor.execute(() -> {
                CachingServiceDiscovery.Refresh previousRefresher = refresh.compareAndExchange(null,
                        new CachingServiceDiscovery.Refresh());
                if (previousRefresher != null) {
                    results.add(previousRefresher.result());
                } else {
                    refresh.get().trigger(() -> fetchNewServiceInstances(refreshCount));
                    results.add(refresh.get().result());
                }
                awaitFinish.countDown();
            });

        }
        assertThat(awaitFinish.await(10, TimeUnit.SECONDS)).isTrue();

        await().atMost(5, TimeUnit.SECONDS)
                .until(refreshCount::get, Matchers.equalTo(1));

        assertThat(results).hasSize(amountOfGets);

        List<ServiceInstance> originalList = null;
        for (Uni<List<ServiceInstance>> result : results) {
            List<ServiceInstance> servicesList = result.await().atMost(Duration.ofSeconds(10));
            assertThat(servicesList).hasSize(2);

            // verify it's the exact same list that is returned:
            if (originalList == null) {
                originalList = servicesList;
            }

            assertThat(servicesList == originalList).isTrue();
        }

    }

    private Uni<List<ServiceInstance>> fetchNewServiceInstances(AtomicInteger refreshCount) {
        refreshCount.incrementAndGet();
        return Uni.createFrom().emitter(e -> {
            List<ServiceInstance> results = asList(new DefaultServiceInstance(1, "localhost", 8406),
                    new DefaultServiceInstance(2, "localhost", 8407));
            vertx.setTimer(2000, ignored -> e.complete(results));
        });
    }
}
