package io.smallrye.stork.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define arrays of {@link ServiceRegistrarAttribute}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceRegistrarAttributes {
    /**
     * @return the array of {@link ServiceRegistrarAttribute}, must not contain {@code null}.
     */
    ServiceRegistrarAttribute[] value();
}
