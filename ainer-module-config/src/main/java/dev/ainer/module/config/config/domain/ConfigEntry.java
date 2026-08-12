package dev.ainer.module.config.config.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A dynamic configuration entry scoped by namespace and key (ADR-0038).
 *
 * <p>Non-secret entries store the raw value in {@code value}. Secret entries store an AES-GCM
 * encrypted ciphertext in {@code encryptedValue} and leave {@code value} null — the plaintext is
 * never persisted for secrets.
 */
public record ConfigEntry(
        UUID id,
        String namespace,
        String key,
        @Nullable String value,
        ConfigValueType valueType,
        boolean secret,
        @Nullable String encryptedValue,
        @Nullable String description,
        long version) {

    public ConfigEntry {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueType, "valueType");
        namespace = namespace.trim();
        key = key.trim();
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (!secret && value == null) {
            throw new IllegalArgumentException("non-secret config must have a value");
        }
        if (secret && encryptedValue == null) {
            throw new IllegalArgumentException("secret config must have an encryptedValue");
        }
    }
}
