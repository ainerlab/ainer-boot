package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Public field projection descriptor (ADR-0030 §5.2, §6.5). Returned by {@code PublicAccessPolicy} when
 * anonymous/public access is allowed; the HTTP adapter must apply this projection before sending the
 * response. A bare boolean ALLOW without a projection is insufficient.
 */
public record PublicProjection(String descriptor) {

    public PublicProjection {
        Objects.requireNonNull(descriptor, "descriptor");
        String normalized = descriptor.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("projection descriptor must not be blank");
        }
        descriptor = normalized;
    }
}
