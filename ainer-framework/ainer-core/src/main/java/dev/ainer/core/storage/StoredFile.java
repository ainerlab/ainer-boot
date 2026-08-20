package dev.ainer.core.storage;

import java.util.Objects;

/**
 * 通过 {@link FileStoragePort} 存储的文件的不可变元数据（ADR-0038）。
 *
 * <p>{@code storageKey} 是适配器专属的不透明标识，用于后续读取或删除。调用方不得假设 key
 * 具有任何结构（依据适配器不同，它可能是路径、blob ID 或 S3 key）。
 *
 * @param storageKey   适配器专属的不透明标识，用于读取/删除
 * @param namespace    用于隔离文件的逻辑分组（例如 workspace 或模块范围）
 * @param filename     原始或生成的文件名（仅用于展示，不用于读取）
 * @param contentType  MIME 类型，未知时为 null
 * @param contentLength 已存储内容的字节长度，未知时为 -1
 */
public record StoredFile(
        String storageKey,
        String namespace,
        String filename,
        String contentType,
        long contentLength) {

    public StoredFile {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(filename, "filename");
        String normalizedKey = storageKey.trim();
        String normalizedNs = namespace.trim();
        String normalizedFn = filename.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        if (normalizedNs.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (normalizedFn.isEmpty()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        storageKey = normalizedKey;
        namespace = normalizedNs;
        filename = normalizedFn;
    }
}
