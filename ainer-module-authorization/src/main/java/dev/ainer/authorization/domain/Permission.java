package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Authorization permission definition (ADR-0030 §3). A stable code plus controlled metadata. The code is
 * the identity; two Permissions with the same code but differing metadata are a startup-failing conflict.
 *
 * @param code            stable permission code
 * @param action          business action verb, e.g. {@code read}/{@code publish}/{@code invoke}
 * @param resourceType    resource type the action targets
 * @param riskTier        risk tier driving ALLOW-vs-CHALLENGE routing
 * @param auditLevel      decision audit level
 * @param systemOnly      only system/platform services may hold/use it
 * @param agentDelegable  may enter an ADR-0031 ActingGrant; orthogonal to Role assignability
 */
public record Permission(
        PermissionCode code,
        String action,
        ResourceType resourceType,
        RiskTier riskTier,
        AuditLevel auditLevel,
        boolean systemOnly,
        boolean agentDelegable) {

    public Permission {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(riskTier, "riskTier");
        Objects.requireNonNull(auditLevel, "auditLevel");
        String normalizedAction = action.trim();
        if (normalizedAction.isEmpty()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        action = normalizedAction;
    }
}
