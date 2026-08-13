package dev.ainer.module.identity.foundation;

/**
 * Security lifecycle status of a {@link HumanAccount} (ADR-0033 Greenfield §3).
 *
 * <p>Only {@code ACTIVE} may authenticate. {@code LOCKED} is a recoverable throttled state; {@code DISABLED}
 * is an admin/security disable that invalidates the account-wide revocation epoch; {@code CLOSED} is terminal
 * (no credential recovery, but downstream resources are preserved per the ADR non-cascade invariant).
 */
public enum AccountStatus {

    ACTIVE,
    LOCKED,
    DISABLED,
    CLOSED;

    /** Only an ACTIVE account may complete authentication. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }

    /** A live account still exists for recovery / governance purposes (not closed or disabled). */
    public boolean isLive() {
        return this == ACTIVE || this == LOCKED;
    }
}
