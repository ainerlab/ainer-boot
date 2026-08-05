package dev.ainer.authorization.domain;

/**
 * Grant path declared by a {@code DomainAuthorizationPolicy} for an authenticated action (ADR-0030 §1, §6.1).
 * {@code PUBLIC} is handled separately by {@code PublicAccessPolicy} and is not a value here.
 */
public enum GrantPath {
    /** Requires a complete owner/participant relation plus authenticated constraints; no SubjectBinding. */
    RELATION_DERIVED,
    /** Requires a live scope-matched SubjectBinding plus policy-declared relation/state. */
    BINDING_REQUIRED,
    /** A complete Binding branch OR a complete relation branch, intersected with state/risk. */
    BINDING_OR_RELATION
}
