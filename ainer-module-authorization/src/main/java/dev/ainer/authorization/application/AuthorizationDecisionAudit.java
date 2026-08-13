package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.AuthorizationOutcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only record of a single authorization decision (ADR-0030 §12.4). Written according to the
 * triggering {@code Permission.auditLevel} — not every read is audited. No Token, prompt, resource
 * body or PII is stored; only stable identity references, permission, outcome, reason and trace ids.
 *
 * @param decisionId      the {@code AuthorizationDecision.decisionId} (UUIDv7, time-ordered)
 * @param workspaceId     workspace context if any, or null
 * @param requesterIssuer issuer namespace of the requester
 * @param requesterType   {@code USER} or {@code SERVICE}
 * @param requesterId     subject id of the requester
 * @param permissionCode  the permission evaluated
 * @param resourceType    resource type if any, or null
 * @param resourceId      resource id if any, or null
 * @param outcome         {@code ALLOW}, {@code DENY} or {@code CHALLENGE}
 * @param reasonCode      stable low-cardinality reason code
 * @param policyVersion   policy version label from the decision
 * @param requestId       request trace id, or null
 * @param traceId         distributed trace id, or null
 * @param evaluatedAt     when the decision was evaluated
 */
public record AuthorizationDecisionAudit(
        UUID decisionId,
        UUID workspaceId,
        String requesterIssuer,
        String requesterType,
        String requesterId,
        String permissionCode,
        String resourceType,
        UUID resourceId,
        AuthorizationOutcome outcome,
        String reasonCode,
        String policyVersion,
        String requestId,
        String traceId,
        Instant evaluatedAt) {

    public AuthorizationDecisionAudit {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(requesterIssuer, "requesterIssuer");
        Objects.requireNonNull(requesterType, "requesterType");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(permissionCode, "permissionCode");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }
}
