package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;

/**
 * Declares the authenticated grant path for an action and evaluates two independent facets
 * (ADR-0030 §5.1, §6.1):
 *
 * <ul>
 *   <li>{@link #relationGrants} — the relation-derived grant (owner/participant relation IS the authority).
 *   <li>{@link #resourceStateSatisfies} — the domain policy's resource state/relation condition that
 *       intersects with ALL grant paths.
 * </ul>
 *
 * The evaluator computes: {@code (bindingGrant ∪ relationGrant) ∩ resourceStateSatisfies} per the declared
 * {@link GrantPath}. Neither facet alone is sufficient; both must hold for the chosen path.
 */
public interface DomainAuthorizationPolicy {

    @Nullable GrantPath pathFor(PermissionCode permission);

    boolean relationGrants(
            Requester.Authenticated subject,
            PermissionCode permission,
            ResourceRef resource,
            AuthorizationContext context);

    boolean resourceStateSatisfies(
            Requester.Authenticated subject,
            PermissionCode permission,
            ResourceRef resource,
            AuthorizationContext context);
}
