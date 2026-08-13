package dev.ainer.module.identity.foundation;

/**
 * Status of an {@link OAuthClientBinding} linking a rotatable OAuth {@code client_id} to a stable
 * {@link ServicePrincipal} (ADR-0033 Greenfield §2.6).
 *
 * <p>Only one {@code ACTIVE} binding may exist per {@code client_id} at a time (enforced by a partial unique
 * index); a {@code RETIRED} binding is preserved for audit and historical token introspection while a fresh
 * credential occupies the same {@code client_id}.
 */
public enum OAuthClientBindingStatus {

    ACTIVE,
    RETIRED;

    /** Whether this binding currently authorises the linked client_id to authenticate as its principal. */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
