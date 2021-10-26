package io.smallrye.stork.servicediscovery.eureka;

import static io.smallrye.stork.servicediscovery.eureka.StorkConfigHelper.get;
import static io.smallrye.stork.servicediscovery.eureka.StorkConfigHelper.getBooleanOrDefault;
import static io.smallrye.stork.servicediscovery.eureka.StorkConfigHelper.getIntegerOrDefault;
import static io.smallrye.stork.servicediscovery.eureka.StorkConfigHelper.getOrDefault;
import static io.smallrye.stork.servicediscovery.eureka.StorkConfigHelper.getOrDie;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.CachingServiceDiscovery;
import io.smallrye.stork.DefaultServiceInstance;
import io.smallrye.stork.ServiceInstance;
import io.smallrye.stork.config.ServiceDiscoveryConfig;
import io.smallrye.stork.spi.ServiceInstanceIds;
import io.smallrye.stork.spi.ServiceInstanceUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

/**
 * Handle service discovery using a Eureka server.
 *
 * It does not use the Eureka client which brings lots of dependencies on the classpath.
 * Eureka exposes a REST API, which can be used easily.
 * The REST API is described on https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.
 */
public class EurekaServiceDiscovery extends CachingServiceDiscovery {

    private final WebClient client;
    private final String path;
    private final boolean secure;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<String> instance;

    public EurekaServiceDiscovery(ServiceDiscoveryConfig config, String serviceName) {
        super(config);
        Vertx vertx = Vertx.vertx(); // TODO Find a way to access the managed instance.

        // Eureka instance
        String host = getOrDie(serviceName, config, "eureka-host");
        int port = getIntegerOrDefault(serviceName, config, "eureka-port", 8761);
        boolean trustAll = getBooleanOrDefault(config, "eureka-trust-all", false);
        boolean eurekaTls = getBooleanOrDefault(config, "eureka-ssl", false);

        // Service selection
        String app = getOrDefault(config, "application", serviceName);
        instance = get(config, "instance");

        // Address selection
        secure = getBooleanOrDefault(config, "secure", false);

        client = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost(host).setDefaultPort(port).setSsl(eurekaTls).setTrustAll(trustAll));

        path = "/eureka/apps/" + app;
    }

    @Override
    public Uni<List<ServiceInstance>> fetchNewServiceInstances(List<ServiceInstance> previousInstances) {
        Uni<HttpResponse<Buffer>> retrieval = client.get(path)
                .putHeader("Accept", "application/json;charset=UTF-8").send();

        return retrieval
                .map(this::getEurekaApplicationInstances)
                .map(this::selectAliveInstances)
                .map(this::selectSecureInstancesIfEnabled)
                .map(this::selectChosenInstanceIfEnabled)
                .map(appInstances -> toStorkServiceInstances(appInstances, previousInstances));
    }

    private Stream<ApplicationInstance> selectSecureInstancesIfEnabled(Stream<ApplicationInstance> stream) {
        if (!secure) {
            return stream;
        } else {
            return stream.filter(i -> i.securePort.enabled);
        }
    }

    private Stream<ApplicationInstance> selectChosenInstanceIfEnabled(Stream<ApplicationInstance> stream) {
        if (instance.isEmpty()) {
            return stream;
        } else {
            return stream.filter(i -> i.instanceId.equalsIgnoreCase(instance.get()));
        }
    }

    private List<ServiceInstance> toStorkServiceInstances(Stream<ApplicationInstance> instances,
            List<ServiceInstance> previousInstances) {
        return instances
                .map(instance -> {
                    String virtualAddress;
                    int port;

                    if (secure && instance.securePort.enabled) {
                        virtualAddress = instance.secureVipAddress;
                        if (virtualAddress == null) {
                            virtualAddress = instance.vipAddress;
                        }
                        port = instance.securePort.port;
                    } else {
                        virtualAddress = instance.vipAddress;
                        port = instance.port.port;
                    }

                    ServiceInstance matching = ServiceInstanceUtils.findMatching(previousInstances, virtualAddress, port);
                    return matching == null ? new DefaultServiceInstance(ServiceInstanceIds.next(), virtualAddress, port)
                            : matching;
                })
                .collect(Collectors.toList());
    }

    private Stream<ApplicationInstance> getEurekaApplicationInstances(HttpResponse<Buffer> response) {
        if (response.statusCode() == 404) {
            return Stream.empty();
        }

        // If it's not 404, it must be 200
        ensure200(response);

        JsonArray array = response.bodyAsJsonObject()
                .getJsonObject("application")
                .getJsonArray("instance");

        return array.stream().map(o -> ((JsonObject) o).mapTo(ApplicationInstance.class));
    }

    private Stream<ApplicationInstance> selectAliveInstances(Stream<ApplicationInstance> instances) {
        return instances
                .filter(ApplicationInstance::isUp);
    }

    private void ensure200(HttpResponse<Buffer> resp) {
        if (resp.statusCode() != 200) {
            throw new RuntimeException(
                    "Unable to retrieve services from Eureka, expected as 200-OK response, but got " + resp.statusCode()
                            + ", body is: " + resp.bodyAsString());
        }
    }
}
