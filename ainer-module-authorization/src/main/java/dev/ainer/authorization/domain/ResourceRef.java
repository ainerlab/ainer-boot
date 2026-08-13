package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a concrete resource targeted by an authorization request (ADR-0030 §4.6, §6.2).
 * {@code workspaceId} is an optional access-context fact. Product ownership/home remains authoritative in
 * the owning module and is not reconstructed from this reference.
 */
public record ResourceRef(
        @Nullable UUID workspaceId,
        ResourceType resourceType,
        UUID resourceId) {

    public ResourceRef {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
    }
}
