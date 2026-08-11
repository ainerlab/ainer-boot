package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Authorization scope bound to a {@link SubjectBinding} (ADR-0030 §4.2). The first version supports only
 * these three scope kinds; nested/recursive scopes and arbitrary JSON conditions are explicitly out of scope.
 *
 * <p>Workspace and resource scopes are independent typed ceilings. Resource ownership is supplied by the
 * product resolver, not inferred from a universal parent concept.
 */
public sealed interface Scope permits Scope.Global, Scope.Workspace, Scope.Resource {

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

    /** Scoped to exactly one Workspace. */
    record Workspace(UUID workspaceId) implements Scope {
        public Workspace {
            Objects.requireNonNull(workspaceId, "workspaceId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            return resource.workspaceId() != null && resource.workspaceId().equals(workspaceId);
        }
    }

    /** Scoped to one concrete resource, anchored to the workspace that owns it. */
    record Resource(UUID workspaceId, ResourceType resourceType, UUID resourceId) implements Scope {
        public Resource {
            Objects.requireNonNull(workspaceId, "workspaceId");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(resourceId, "resourceId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            return resourceType.equals(resource.resourceType())
                    && resourceId.equals(resource.resourceId());
        }
    }
}
