package dev.ainer.module.file.file.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Size and content-type limits for uploads (ADR-0040 spec: 大小/类型限制). Fail-closed defaults:
 * a blank content type is always rejected; only listed types are accepted. Override via
 * {@code ainer.file.max-size-bytes} / {@code ainer.file.allowed-content-types}.
 */
@ConfigurationProperties(prefix = "ainer.file")
public record FileStorageProperties(long maxSizeBytes, Set<String> allowedContentTypes) {

    private static final long DEFAULT_MAX_SIZE_BYTES = 52_428_800L; // 50 MB

    private static final Set<String> DEFAULT_ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "application/json",
            "application/zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "video/mp4");

    public FileStorageProperties {
        if (maxSizeBytes <= 0) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = DEFAULT_ALLOWED_CONTENT_TYPES;
        } else {
            allowedContentTypes = Set.copyOf(allowedContentTypes);
        }
    }
}
