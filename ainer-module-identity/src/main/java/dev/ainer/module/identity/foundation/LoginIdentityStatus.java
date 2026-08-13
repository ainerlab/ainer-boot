package dev.ainer.module.identity.foundation;

/**
 * Lifecycle status of a {@link LoginIdentity} binding (ADR-0033 Greenfield §4).
 *
 * <p>{@code ACTIVE} may be used to authenticate (subject to its {@link HumanAccount} status and epoch).
 * {@code REVOKED} is terminal for that binding — unlink / re-link requires a fresh verification ceremony and
 * produces a new binding; it must never resurrect access on its own.
 */
public enum LoginIdentityStatus {

    ACTIVE,
    REVOKED
}
