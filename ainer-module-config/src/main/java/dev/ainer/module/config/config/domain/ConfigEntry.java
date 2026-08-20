package dev.ainer.module.config.config.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 以 namespace 和 key 定界的动态配置条目（ADR-0038）。
 *
 * <p>非 secret 条目把原始值存在 {@code value}。secret 条目把 AES-GCM 加密后的密文存在
 * {@code encryptedValue} 并让 {@code value} 为 null——secret 的明文绝不持久化。
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
