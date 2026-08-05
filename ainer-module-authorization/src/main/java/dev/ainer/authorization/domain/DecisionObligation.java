package dev.ainer.authorization.domain;

/**
 * Typed obligation attached to an ALLOW decision (ADR-0030 §6.5). The caller must execute every obligation
 * before the decision's effect reaches the client. Unknown, unsupported, or execution-failed obligations
 * default deny. S0 only implements {@link PublicProjection}; additional obligation types (DataClassificationCeiling,
 * Watermark, AuthorizedUntil, RecheckBefore) are added when real use cases require them.
 */
public sealed interface DecisionObligation permits PublicProjection {
}
