package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable authorization decision (ADR-0030 §6). {@link AuthorizationOutcome#CHALLENGE} means the action
 * must not execute until the requested challenge is satisfied and the decision re-evaluated; it is never
 * an ALLOW. {@code policyVersion} supports audit and re-evaluation and is not a signed authorization
 * credential.
 */
public record AuthorizationDecision(
        UUID decisionId,
        AuthorizationOutcome outcome,
        ReasonCode reasonCode,
        String policyVersion,
        Instant evaluatedAt,
        @Nullable Instant validUntil) {

    public AuthorizationDecision {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public boolean isAllowed() {
        return outcome == AuthorizationOutcome.ALLOW;
    }

    public static AuthorizationDecision allow(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                UUID.randomUUID(), AuthorizationOutcome.ALLOW, reasonCode, policyVersion, evaluatedAt, null);
    }

    public static AuthorizationDecision deny(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                UUID.randomUUID(), AuthorizationOutcome.DENY, reasonCode, policyVersion, evaluatedAt, null);
    }

    public static AuthorizationDecision challenge(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                UUID.randomUUID(), AuthorizationOutcome.CHALLENGE, reasonCode, policyVersion, evaluatedAt, null);
    }
}
