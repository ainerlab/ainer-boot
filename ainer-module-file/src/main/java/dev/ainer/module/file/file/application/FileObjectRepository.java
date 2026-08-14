package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileObject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link FileObject} metadata (ADR-0040).
 */
public interface FileObjectRepository {

    void insert(FileObject object);

    Optional<FileObject> findById(UUID id);

    /**
     * Page through metadata, optionally filtered by namespace, newest first.
     *
     * @param namespace optional namespace filter; {@code null} or blank means all namespaces
     * @param offset    zero-based row offset, computed by the caller from page/size
     * @param size      page size (already validated to 1..100)
     */
    FilePageSlice findPage(@Nullable String namespace, long offset, int size);

    /** Delete the metadata row. Returns false when the row does not exist. */
    boolean deleteById(UUID id);

    /** One page of metadata plus the total row count for pagination. */
    record FilePageSlice(List<FileObject> items, long total) {
    }
}
