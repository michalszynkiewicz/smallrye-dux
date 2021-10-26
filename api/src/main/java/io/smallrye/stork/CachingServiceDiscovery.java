package io.smallrye.stork;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.config.ServiceDiscoveryConfig;
import io.smallrye.stork.utils.DurationUtils;

/**
 * Adds the ability to be cached to the ServiceDiscovery implementations.
 *
 * <br>
 * <b>Must be non-blocking</b>
 */
public abstract class CachingServiceDiscovery implements ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(CachingServiceDiscovery.class);

    public final Duration refreshPeriod;
    public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofMinutes(5);

    private volatile ServiceInstancesCache cacheData;
    // non-private for tests only
    final AtomicReference<Refresh> refresh = new AtomicReference<>(null);

    public CachingServiceDiscovery(ServiceDiscoveryConfig config) {
        String period = config.parameters().get("refresh-period");
        try {
            // TODO: document it
            this.refreshPeriod = period != null
                    ? DurationUtils.parseDuration(period)
                    : DEFAULT_REFRESH_INTERVAL;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("refresh-period for service discovery should be a number, got: " +
                    period,
                    e);
        }
    }

    /**
     *
     * @return all `ServiceInstance`'s for the service
     */
    public Uni<List<ServiceInstance>> getServiceInstances() {
        if (cacheData == null) {
            Refresh previousRefresher = refresh.compareAndExchange(null, new Refresh());
            if (previousRefresher != null) {
                return previousRefresher.result();
            } else {
                Refresh refresh = this.refresh.get();
                refresh.trigger(() -> fetchNewServiceInstances(Collections.emptyList()));
                return refresh.result().onItem().invoke(this::replaceCacheData)
                        .onFailure().invoke(this::handleFetchError);
            }
        } else {
            if (refreshNotNeed()) {
                // this should be the most used path by far
                return Uni.createFrom().item(cacheData.getServiceInstances());
            } else {
                Refresh previousRefresher = refresh.compareAndExchange(null, new Refresh());
                if (previousRefresher != null) {
                    return previousRefresher.result();
                } else {
                    Refresh refresh = this.refresh.get();
                    refresh.trigger(() -> fetchNewServiceInstances(cacheData.getServiceInstances()));
                    return refresh.result().onItem().invoke(this::replaceCacheData)
                            .onFailure().invoke(this::handleFetchError);
                }
            }
        }
    }

    private void handleFetchError(Throwable error) {
        log.error("Failed to fetch service instances", error);
        refresh.set(null);
    }

    private void replaceCacheData(List<ServiceInstance> serviceInstances) {
        cacheData = new ServiceInstancesCache(serviceInstances, LocalDateTime.now());
        refresh.set(null);
    }

    public boolean refreshNotNeed() {
        return cacheData.getLastFetchDateTime().isAfter(LocalDateTime.now().minus(refreshPeriod));
    }

    public abstract Uni<List<ServiceInstance>> fetchNewServiceInstances(List<ServiceInstance> previousInstances);

    public static class Refresh {

        final CompletableFuture<List<ServiceInstance>> result = new CompletableFuture<>();
        final Uni<List<ServiceInstance>> uniResult = Uni.createFrom().completionStage(result).memoize().indefinitely();

        protected void trigger(Supplier<Uni<List<ServiceInstance>>> supplier) {
            try {
                Uni<List<ServiceInstance>> instances = supplier.get();
                instances.subscribe().with(result::complete, result::completeExceptionally);
            } catch (Throwable any) {
                result.completeExceptionally(any);
            }
        }

        public Uni<List<ServiceInstance>> result() {
            return uniResult;
        }
    }
}
