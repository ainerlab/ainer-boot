package dev.ainer.authorization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only record of a management action on the authorization catalog (ADR-0030 §11.7, §12.4).
 * Role/Binding changes must be persisted in the same transaction as the change; an audit write
 * failure rolls back the business change. No Token, credential, prompt or resource body is stored.
 *
 * @param id            audit row identity (DB-generated UUIDv7)
 * @param actorIssuer   issuer namespace of the actor, or null for a system-initiated change
 * @param actorType     {@code USER} or {@code SERVICE}, or null
 * @param actorId       subject id of the actor, or null
 * @param targetType    the kind of target changed ({@code ROLE}, {@code BINDING})
 * @param targetId      the primary key of the target
 * @param action        the action performed ({@code CREATE}, {@code REPLACE_PERMISSIONS}, {@code REVOKE})
 * @param beforeVersion the target version before the change, or null for create
 * @param afterVersion  the target version after the change, or null
 * @param requestId     request trace id, or null
 * @param traceId       distributed trace id, or null
 * @param occurredAt    when the action occurred
 */
public record AuthorizationChangeAudit(
        UUID id,
        String actorIssuer,
        String actorType,
        String actorId,
        String targetType,
        UUID targetId,
        String action,
        Long beforeVersion,
        Long afterVersion,
        String requestId,
        String traceId,
        Instant occurredAt) {

    public AuthorizationChangeAudit {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(occurredAt, "occurredAt");
        String normalizedTarget = targetType.trim();
        String normalizedAction = action.trim();
        if (normalizedTarget.isEmpty()) {
            throw new IllegalArgumentException("targetType must not be blank");
        }
        if (normalizedAction.isEmpty()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        targetType = normalizedTarget;
        action = normalizedAction;
    }
}
