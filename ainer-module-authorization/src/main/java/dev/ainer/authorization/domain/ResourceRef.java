package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a concrete resource targeted by an authorization request (ADR-0030 §4.6, §6.2).
 * {@code authoritativeTenantId} is non-null for tenant-owned resources and may be null only for explicit
 * platform-global resources; tenant-owned resources never use a nullable tenant to bypass scope checks.
 */
public record ResourceRef(
        @Nullable UUID authoritativeTenantId,
        ResourceType resourceType,
        UUID resourceId) {

    public ResourceRef {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
    }
}
