package dev.ainer.authorization.domain;

/**
 * Risk tier attached to a Permission (ADR-0030 §3.1, §6). Higher tiers may route an ALLOW into a
 * {@link AuthorizationOutcome#CHALLENGE} requiring step-up, transaction confirmation or human approval.
 */
public enum RiskTier {
    LOW,
    MEDIUM,
    HIGH
}
