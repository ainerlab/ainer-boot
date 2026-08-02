package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Authorization scope bound to a {@link SubjectBinding} (ADR-0030 §4.2). The first version supports only
 * these three scope kinds; nested/recursive scopes and arbitrary JSON conditions are explicitly out of scope.
 *
 * <p>{@code Resource} always carries an authoritative tenant; tenant-owned resources never use a nullable
 * tenant to bypass scope checks.
 */
public sealed interface Scope permits Scope.Global, Scope.Tenant, Scope.Resource {

    /**
     * Whether this scope authoritatively covers the given resource (ADR-0030 §6.2). No recursive parent
     * traversal, path wildcards or scope trees in the first version.
     */
    boolean covers(ResourceRef resource);

    /** Platform-global scope; only controlled platform services may hold it. */
    record Global() implements Scope {
        @Override
        public boolean covers(ResourceRef resource) {
            return true;
        }
    }

    /** Scoped to exactly one tenant. */
    record Tenant(UUID tenantId) implements Scope {
        public Tenant {
            Objects.requireNonNull(tenantId, "tenantId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            return resource.authoritativeTenantId() != null
                    && resource.authoritativeTenantId().equals(tenantId);
        }
    }

    /** Scoped to one concrete resource within a tenant. */
    record Resource(UUID tenantId, ResourceType resourceType, UUID resourceId) implements Scope {
        public Resource {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(resourceId, "resourceId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            return tenantId.equals(resource.authoritativeTenantId())
                    && resourceType.equals(resource.resourceType())
                    && resourceId.equals(resource.resourceId());
        }
    }
}
