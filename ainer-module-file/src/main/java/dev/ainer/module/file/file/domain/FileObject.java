package dev.ainer.module.file.file.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted metadata of one stored file object (ADR-0040). The bytes live behind
 * {@link dev.ainer.core.storage.FileStoragePort}; this record is the durable, queryable fact about
 * the upload: where it is, what it claims to be, who uploaded it and when.
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
