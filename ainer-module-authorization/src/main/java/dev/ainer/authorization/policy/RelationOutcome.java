package dev.ainer.authorization.policy;

/**
 * Outcome of a relation-derived authorization check (ADR-0030 §6.1).
 */
public enum RelationOutcome {
    /** The complete owner/participant relation plus state grants the action. */
    ALLOWED,
    /** The relation policy explicitly does not grant the action. */
    DENIED
}
