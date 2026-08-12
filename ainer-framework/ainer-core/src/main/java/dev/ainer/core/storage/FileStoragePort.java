package dev.ainer.core.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Port for file storage operations (ADR-0038). Ainer-Boot provides a local-filesystem adapter;
 * products can supply S3/OSS/MinIO adapters by implementing this interface.
 *
 * <p>The port is intentionally minimal — store, resolve (download stream) and delete. Metadata
 * persistence (who uploaded, when, business associations) is the product's responsibility, not the
 * storage port's. The {@code namespace} parameter provides logical isolation (e.g. per-workspace or
 * per-module directories) without coupling the port to any business concept.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>be idempotent on {@link #delete} (return {@code false} if the key does not exist, not throw);</li>
 *   <li>generate a {@code storageKey} that is unique within the namespace and survives adapter restarts;</li>
 *   <li>validate namespace/filename to prevent path traversal (the local adapter rejects {@code ..} and
 *       absolute paths);</li>
 *   <li>close the caller's {@link InputStream} after reading (or on failure).</li>
 * </ul>
 */
public interface FileStoragePort {

    /**
     * Store a file under the given namespace.
     *
     * @param namespace   logical grouping for isolation (e.g. a workspace or module scope)
     * @param filename    original or generated filename (display only)
     * @param contentType MIME type, or null if unknown
     * @param content     file content stream (will be consumed and closed by the implementation)
     * @return stored file metadata including the generated storage key
     * @throws FileStorageException if the content cannot be read or persisted
     */
    StoredFile store(String namespace, String filename, String contentType, InputStream content);

    /**
     * Open an input stream for reading a previously stored file.
     *
     * @param storageKey the key returned by {@link #store}
     * @return the content stream, or empty if the key does not exist
     * @throws FileStorageException if the stream cannot be opened
     */
    Optional<InputStream> resolve(String storageKey);

    /**
     * Delete a stored file. Idempotent — returns {@code false} if the key does not exist.
     *
     * @param storageKey the key returned by {@link #store}
     * @return true if a file was deleted, false if the key was not found
     * @throws FileStorageException if deletion fails for a reason other than not-found
     */
    boolean delete(String storageKey);
}
