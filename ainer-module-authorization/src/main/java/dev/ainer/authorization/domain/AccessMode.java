package dev.ainer.authorization.domain;

/**
 * Access mode selected by the endpoint/use-case contract (ADR-0030 §5.7). {@link #PUBLIC_PROJECTION}
 * enters only the public pipeline; {@link #AUTHENTICATED} enters only the authenticated pipeline. The mode
 * is fixed server-side and never chosen by client header/query/body, and one path never auto-falls-back to
 * the other.
 */
public enum AccessMode {
    PUBLIC_PROJECTION,
    AUTHENTICATED
}
