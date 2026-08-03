package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable authorization decision (ADR-0030 §6). {@link AuthorizationOutcome#CHALLENGE} means the action
 * must not execute until {@link #challenge()} is satisfied and the decision re-evaluated; it is never an
 * ALLOW. {@link #obligations()} carries typed constraints the caller must execute before the effect reaches
 * the client.
 *
 * <p>{@code decisionId} is a UUIDv7 (RFC 9562) — time-ordered for audit correlation, consistent with Ainer's
 * PostgreSQL 18 {@code uuidv7()} convention (ADR-0020).
 */
public record AuthorizationDecision(
        UUID decisionId,
        AuthorizationOutcome outcome,
        ReasonCode reasonCode,
        String policyVersion,
        Instant evaluatedAt,
        @Nullable Instant validUntil,
        @Nullable Challenge challenge,
        List<DecisionObligation> obligations) {

    public AuthorizationDecision {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        obligations = obligations != null ? List.copyOf(obligations) : List.of();
    }

    public boolean isAllowed() {
        return outcome == AuthorizationOutcome.ALLOW;
    }

    private static UUID newDecisionId() {
        long timestampMs = System.currentTimeMillis();
        var random = ThreadLocalRandom.current();
        long msb = (timestampMs << 16) | (0x7L << 12) | random.nextInt(4096);
        long lsb = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }

    public static AuthorizationDecision allow(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.ALLOW, reasonCode, policyVersion, evaluatedAt, null, null, List.of());
    }

    public static AuthorizationDecision allowPublic(
            ReasonCode reasonCode, String policyVersion, Instant evaluatedAt, PublicProjection projection) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.ALLOW, reasonCode, policyVersion,
                evaluatedAt, null, null, List.of(projection));
    }

    public static AuthorizationDecision deny(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.DENY, reasonCode, policyVersion, evaluatedAt, null, null, List.of());
    }

    public static AuthorizationDecision challengeAuthentication(
            ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.CHALLENGE, reasonCode, policyVersion, evaluatedAt,
                null, new Challenge.AuthenticationChallenge(AuthorizationContext.Assurance.RECENT_STRONG), List.of());
    }
}
