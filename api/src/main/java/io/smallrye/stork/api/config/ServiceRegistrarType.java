package io.smallrye.stork.api.config;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.smallrye.stork.api.MetadataKey;
import io.smallrye.stork.spi.ServiceRegistrarProvider;

/**
 * The type of the service registrar. Use this annotation on your {@link ServiceRegistrarProvider}
 * <p>
 * The type is used to determine service registrar.
 * <p>
 * Use {@code stork.service-registration.my-registration=my-service-registrar} to use the
 * {@link ServiceRegistrarProvider} annotated with {@code @ServiceRegistrar("my-service-registrar")}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(TYPE)
public @interface ServiceRegistrarType {
    /**
     * @return the type of the service registrar
     */
    String value();

    /**
     * @return metadata key type for the service registrar. Must match the second type argument of the service registrar
     *         provider
     */
    Class<? extends MetadataKey> metadataKey();
}
