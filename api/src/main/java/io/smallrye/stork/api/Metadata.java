package io.smallrye.stork.api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Service Instance metadata.
 *
 * <p>
 * This class stores service instance metadata that can be used by the load balancer to select the service instance to use.
 * </p>
 * <p>
 * Instances of this class are <strong>immutable</strong>. Modification operations return new instances.
 * </p>
 * You can create new instances using the {@link #of(Class)}, {@link #of(Class, Map)} and {@link #with(Enum, Object)}methods.
 */
public class Metadata<T extends Enum<T> & MetadataKey> {

    private final EnumMap<T, Object> metadata;
    private final Class<T> clazz;
    private static final Metadata EMPTY = new Metadata<>(NoMetadataKey.class, Collections.emptyMap());

    private Metadata(Class<T> key, Map<T, Object> metadata) {
        if (metadata.isEmpty()) {
            this.metadata = new EnumMap<>(key);
        } else {
            this.metadata = new EnumMap<>(metadata);
        }
        this.clazz = key;
    }

    /**
     * Returns an immutable, empty set of metadata.
     *
     * @return the empty instance
     */
    public static <T extends Enum<T> & MetadataKey> Metadata<T> empty() {
        return (Metadata<T>) EMPTY;
    }

    /**
     * Returns an instance of {@link Metadata} containing metadata values.
     *
     * @param key the class of the key, must not be {@code null}
     * @param metadata the metadata, must not be {@code null}, must not contain {@code null},
     *        must not contain multiple objects of the same class
     * @param <K> the key type
     * @return the new metadata
     */
    public static <K extends Enum<K> & MetadataKey> Metadata<K> of(Class<K> key, Map<K, Object> metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("`metadata` must not be `null`");
        }
        return new Metadata<>(key, metadata);
    }

    /**
     * Returns an instance of {@link Metadata} containing an empty set of values.
     *
     * @param key the type of metadata, must not be {@code null}
     * @param <K> the type key type
     * @return the new metadata
     */
    public static <K extends Enum<K> & MetadataKey> Metadata<K> of(Class<K> key) {
        if (key == null) {
            throw new IllegalArgumentException("`key` must not be `null`");
        }
        return new Metadata<>(key, Collections.emptyMap());
    }

    /**
     * Creates a new instance of {@link Metadata} with the current entries, plus {@code item}.
     * If the current set of metadata contains already an instance of the class of {@code item}, the value is replaced
     * in the returned {@link Metadata}.
     *
     * @param key the key, must not be {@code null}
     * @param item the metadata to be added, must not be {@code null}.
     * @return the new instance of {@link Metadata}
     */
    public Metadata<T> with(T key, Object item) {
        if (key == null) {
            throw new IllegalArgumentException("`key` must not be `null`");
        }
        if (item == null) {
            throw new IllegalArgumentException(key.name() + " should not be `null`");
        }
        EnumMap<T, Object> copy = new EnumMap<>(metadata);
        copy.put(key, item);
        return new Metadata<>(this.clazz, copy);
    }

    /**
     * @return the metadata
     */
    public EnumMap<T, Object> getMetadata() {
        return metadata;
    }

    /**
     * The default metadata key.
     */
    public enum DefaultMetadataKey implements MetadataKey {
        /**
         * The key.
         */
        GENERIC_METADATA_KEY("generic");

        private final String name;

        DefaultMetadataKey(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum NoMetadataKey implements MetadataKey {
        ;

        @Override
        public String getName() {
            return null;
        }
    }
}
