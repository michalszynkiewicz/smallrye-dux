package io.smallrye.stork.api.config;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// mstodo update javadogz
/**
 * Defines an attribute for a service registrar.
 *
 * The name of the attribute corresponds to the value used in the configuration, e.g. for name {@code my-attribute},
 * use the following to set the value:
 * // * mstodo:
 * {@code stork.<my-service>.load-balancer.my-attribute}
 *
 * In the configuration class generated for the load balancer, this attribute will be exposed through {@code getMyAttribute()}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(TYPE)
@Repeatable(ServiceRegistrarAttributes.class)
public @interface ServiceRegistrarAttribute {
    /**
     * Attribute name as used in the configuration. Exposed through a getter with name converted to camelCase with
     * characters that cannot be used in a java identifiers filtered out.
     *
     * @return the name of the configuration property
     */
    String name();

    /**
     * Default value for the attribute. If not provided and user didn't set the value - null will be passed
     * 
     * @return the default value
     */
    String defaultValue() default Constants.DEFAULT_VALUE;

    /**
     * Description of the attribute. Works best in the documentation if it starts with a capital letter and ends with period.
     * 
     * @return the description
     */
    String description();

    /**
     * Whether the attribute is mandatory or optional
     * 
     * @return true if the attribute is required, false otherwise
     */
    boolean required() default false;
}
