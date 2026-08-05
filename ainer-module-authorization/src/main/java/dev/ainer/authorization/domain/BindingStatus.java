package dev.ainer.authorization.domain;

/**
 * Lifecycle status of a {@link SubjectBinding} (ADR-0030 §4.1, §11.2). Revocation is a logical state
 * transition, not a physical delete.
 */
public enum BindingStatus {
    ACTIVE,
    REVOKED
}
