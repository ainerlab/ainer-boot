package dev.ainer.authorization.domain;

/**
 * Outcome of an authorization evaluation (ADR-0030 §6). {@link #CHALLENGE} means the action must not
 * proceed until the requested factor/confirmation/approval is satisfied, after which the decision must be
 * re-evaluated; it is not an ALLOW.
 */
public enum AuthorizationOutcome {
    ALLOW,
    DENY,
    CHALLENGE
}
