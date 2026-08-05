package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Single-resource authorization request (ADR-0030 §6). The {@link AccessMode} is fixed by the endpoint /
 * use-case contract and selects exactly one pipeline; one path never auto-falls-back to the other.
 */
public record AuthorizationRequest(
        Requester requester,
        AccessMode accessMode,
        PermissionCode permission,
        ResourceRef resource,
        AuthorizationContext context) {

    public AuthorizationRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(accessMode, "accessMode");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(context, "context");
    }
}
