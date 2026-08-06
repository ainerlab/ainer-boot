package dev.ainer.module.identity.foundation;

/**
 * Lifecycle status of a {@link Credential} (ADR-0033 Greenfield §4).
 *
 * <p>Only {@code ACTIVE} material takes part in authentication. {@code REVOKED} marks material superseded by
 * a rotation or otherwise invalidated for future use; it is kept for audit while the new ACTIVE material for
 * the same {@code (account, type)} is inserted.
 */
public enum CredentialStatus {

    ACTIVE,
    REVOKED
}