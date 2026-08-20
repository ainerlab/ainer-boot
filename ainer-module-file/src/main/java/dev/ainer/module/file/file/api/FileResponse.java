package dev.ainer.module.file.file.api;

import dev.ainer.module.file.file.domain.FileObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** {@link FileObject} 元数据的对外投影（ADR-0040）。绝不暴露存储键。 */
public record FileResponse(
        UUID id,
        String namespace,
        String filename,
        @Nullable String contentType,
        long contentLength,
        @Nullable String checksumSha256,
        @Nullable UUID workspaceId,
        String uploadedByType,
        Instant createdAt) {

    public static FileResponse from(FileObject object) {
        return new FileResponse(
                object.id(),
                object.namespace(),
                object.filename(),
                object.contentType(),
                object.contentLength(),
                object.checksumSha256(),
                object.workspaceId(),
                object.uploadedByType(),
                object.createdAt());
    }
}
