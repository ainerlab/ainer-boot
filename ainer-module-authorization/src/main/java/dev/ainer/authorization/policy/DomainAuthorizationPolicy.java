package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;

/**
 * Declares the authenticated grant path for an action and evaluates relation-derived grants
 * (ADR-0030 §5.1, §6.1). The policy is registered statically and validated at registration; it never
 * switches with the request. Relation facts are read only through owning-module ports.
 */
public interface DomainAuthorizationPolicy {

    GrantPath pathFor(PermissionCode permission);

    RelationOutcome relationAllows(
            Requester.Authenticated subject,
            PermissionCode permission,
            ResourceRef resource,
            AuthorizationContext context);
}
