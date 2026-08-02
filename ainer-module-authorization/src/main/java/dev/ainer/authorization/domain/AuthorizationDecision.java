package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable authorization decision (ADR-0030 §6). {@link AuthorizationOutcome#CHALLENGE} means the action
 * must not execute until the requested challenge is satisfied and the decision re-evaluated; it is never
 * an ALLOW. {@code publicProjection} carries the required field projection for PUBLIC ALLOW.
 *
 * <p>{@code decisionId} is a <strong>UUIDv7</strong> (RFC 9562) — time-ordered for audit correlation,
 * consistent with Ainer's PostgreSQL 18 {@code uuidv7()} convention (ADR-0020).
 */
public record AuthorizationDecision(
        UUID decisionId,
        AuthorizationOutcome outcome,
        ReasonCode reasonCode,
        String policyVersion,
        Instant evaluatedAt,
        @Nullable Instant validUntil,
        @Nullable PublicProjection publicProjection) {

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

    /**
     * Generates a UUIDv7 (RFC 9562): 48-bit Unix-millisecond timestamp + version 7 + 74 random bits.
     * Time-ordered for audit sortability; generated in-JVM because the decision is evaluated before any
     * database round-trip. Persisted audit rows in S1 use PostgreSQL {@code uuidv7()} for their own PKs
     * and store this decisionId as a correlation column.
     */
    private static UUID newDecisionId() {
        long timestampMs = System.currentTimeMillis();
        var random = ThreadLocalRandom.current();
        long msb = (timestampMs << 16) | (0x7L << 12) | random.nextInt(4096);
        long lsb = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }

    public static AuthorizationDecision allow(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.ALLOW, reasonCode, policyVersion, evaluatedAt, null, null);
    }

    public static AuthorizationDecision allowPublic(
            ReasonCode reasonCode, String policyVersion, Instant evaluatedAt, PublicProjection projection) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.ALLOW, reasonCode, policyVersion, evaluatedAt, null, projection);
    }

    public static AuthorizationDecision deny(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.DENY, reasonCode, policyVersion, evaluatedAt, null, null);
    }

    public static AuthorizationDecision challenge(ReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
        return new AuthorizationDecision(
                newDecisionId(), AuthorizationOutcome.CHALLENGE, reasonCode, policyVersion, evaluatedAt, null, null);
    }
}
