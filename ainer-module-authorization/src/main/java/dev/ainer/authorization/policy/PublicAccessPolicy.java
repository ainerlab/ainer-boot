package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceRef;

/**
 * The sole grant path for anonymous access (ADR-0030 §1, §5.2). Absent an explicit policy, public access
 * defaults to deny. Authenticated callers using {@link dev.ainer.authorization.domain.AccessMode#PUBLIC_PROJECTION}
 * receive the same public projection as anonymous.
 */
@FunctionalInterface
public interface PublicAccessPolicy {

    boolean allows(PermissionCode permission, ResourceRef resource);
}
