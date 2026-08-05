package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.PublicProjection;
import dev.ainer.authorization.domain.ResourceRef;

import java.util.Optional;

/**
 * The sole grant path for anonymous/public access (ADR-0030 §1, §5.2). Returns an empty Optional when
 * public access does not apply; a non-empty {@link PublicProjection} when it does. The projection
 * descriptor is attached as an obligation on the ALLOW decision, and the HTTP adapter must apply it before
 * sending the response. Absent an explicit policy, public access defaults to deny.
 */
@FunctionalInterface
public interface PublicAccessPolicy {

    Optional<PublicProjection> evaluate(PermissionCode permission, ResourceRef resource);
}
