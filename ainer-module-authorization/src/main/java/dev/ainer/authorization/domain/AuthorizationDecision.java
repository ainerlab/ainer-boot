package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 不可变的授权决策（ADR-0030 §6）。{@link AuthorizationOutcome#CHALLENGE} 表示动作在
 * {@link #challenge()} 被满足并重新求值之前不得执行，它绝不是 ALLOW。{@link #obligations()}
 * 携带调用方必须执行的类型化约束，效果触达客户端之前必须全部完成。
 *
 * <p>{@code decisionId} 是 UUIDv7（RFC 9562）——时间有序以便审计关联，与 Ainer 的
 * PostgreSQL 18 {@code uuidv7()} 约定一致（ADR-0020）。
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
