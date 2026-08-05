package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.PermissionCode;

/**
 * Maps an OAuth scope to the permissions it permits (ADR-0030 §3.3). A scope never becomes a permission
 * implicitly by name equality; only this explicit ceiling mapper authorizes authenticated paths.
 */
@FunctionalInterface
public interface ScopePermissionCeiling {

    boolean permits(String scope, PermissionCode permission);
}
