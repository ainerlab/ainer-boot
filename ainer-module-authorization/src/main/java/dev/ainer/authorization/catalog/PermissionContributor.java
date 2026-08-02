package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.Permission;

import java.util.Set;

/**
 * Supplies a bounded set of {@link Permission} definitions implemented by the contributing module
 * (ADR-0030 §3.2). The Permission catalog is a management projection; administrators cannot create
 * arbitrary permission strings not implemented by application code.
 */
@FunctionalInterface
public interface PermissionContributor {

    Set<Permission> contribute();
}
