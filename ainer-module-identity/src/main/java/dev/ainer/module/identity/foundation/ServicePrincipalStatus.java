package dev.ainer.module.identity.foundation;

/**
 * Lifecycle status of a {@link ServicePrincipal} (ADR-0033 Greenfield §2.6).
 *
 * <p>The C1 foundation baseline keeps the service lifecycle intentionally minimal: a service principal is
 * either {@code ACTIVE} (can authenticate and bind credentials) or {@code DISABLED} (revoked; credentials
 * and tokens minted before the current epoch are invalid). Granular lock/close states are deferred until a
 * concrete operational need arrives.
 */
public enum ServicePrincipalStatus {

    ACTIVE,
    DISABLED;

    /** Whether this principal can authenticate or hold an active credential binding. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
