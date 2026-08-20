package dev.ainer.module.file.file.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单个已存储文件对象的持久化元数据（ADR-0040）。字节保存在
 * {@link dev.ainer.core.storage.FileStoragePort} 背后；本 record 是关于这次上传的持久、
 * 可查询事实：它在哪里、声明自己是什么、谁在何时上传。
 */
public record FileObject(
        UUID id,
        String storageKey,
        String namespace,
        String filename,
        @Nullable String contentType,
        long contentLength,
        @Nullable String checksumSha256,
        @Nullable UUID workspaceId,
        String uploadedByIssuer,
        String uploadedByType,
        String uploadedById,
        Instant createdAt) {

    public FileObject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(namespace, "namespace");
        filename = requireText(filename, "filename");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must be non-negative");
        }
        Objects.requireNonNull(uploadedByIssuer, "uploadedByIssuer");
        Objects.requireNonNull(uploadedById, "uploadedById");
        if (!"USER".equals(uploadedByType) && !"SERVICE".equals(uploadedByType)) {
            throw new IllegalArgumentException("uploadedByType must be USER or SERVICE");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }
}
