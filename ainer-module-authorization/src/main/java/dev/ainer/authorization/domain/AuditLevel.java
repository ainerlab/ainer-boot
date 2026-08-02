package dev.ainer.authorization.domain;

/**
 * Audit level attached to a Permission (ADR-0030 §3.1, §12). Controls whether a decision is persisted to
 * the authorization decision audit.
 */
public enum AuditLevel {
    /** No decision row written (typical for bulk public reads). */
    NONE,
    /** Decision row written only for ALLOW of protected actions /DENY/CHALLENGE. */
    ON_DECISION,
    /** Every evaluation audited. */
    ALWAYS
}
