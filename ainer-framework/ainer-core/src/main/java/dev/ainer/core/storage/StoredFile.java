package dev.ainer.core.storage;

import java.util.Objects;

/**
 * Immutable metadata of a file stored through {@link FileStoragePort} (ADR-0038).
 *
 * <p>The {@code storageKey} is the opaque, adapter-specific identifier used to retrieve or delete the
 * file later. Callers must not assume any structure in the key (it may be a path, a blob ID, or an S3
 * key depending on the adapter).
 *
 * @param storageKey   adapter-specific opaque identifier for retrieval/deletion
 * @param namespace    logical grouping (e.g. workspace or module scope) to isolate files
 * @param filename     original or generated filename (display only, not used for retrieval)
 * @param contentType  MIME type, or null if unknown
 * @param contentLength byte length of the stored content, or -1 if unknown
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
