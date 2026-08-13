package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;

/**
 * Code-registered, versioned policy controlling authorization administration (ADR-0030 §11).
 *
 * <p>Using a permission and assigning it are deliberately separate capabilities. Implementations
 * define the exact trusted management principals and the permissions, scopes and target subjects
 * they may assign. These decisions must come from product/platform code, not from the actor's
 * effective access and not from arbitrary request data.
 *
 * <p>The generic module installs a deny-all implementation. A host application must explicitly
 * contribute a policy before any authorization-management endpoint or mutation becomes usable.
 */
public interface GrantAdministrationPolicy {

    /** Stable version recorded by the host for policy rollout and audit correlation. */
    String version();

    /** Whether this exact verified principal is a trusted authorization manager. */
    boolean isTrustedManager(AuthenticatedPrincipal actor);

    /** Whether the manager may place this registered permission in a managed Role. */
    boolean isPermissionAssignable(AuthenticatedPrincipal actor, Permission permission);

    /** Whether the manager may create a Binding with this structured scope. */
    boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope);

    /** Whether the manager may create a Binding for this authority-qualified target. */
    boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target);
}
