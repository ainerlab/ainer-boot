package dev.ainer.authorization.api;

import dev.ainer.authorization.application.RoleRepository;
import dev.ainer.authorization.application.SubjectBindingRepository;
import dev.ainer.authorization.application.SubjectSetBindingRepository;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Scope;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request and response DTOs for the authorization management API (ADR-0030 S2).
 * All records are public; response factories are package-private static {@code from(...)} methods.
 */
public final class AuthorizationApiDtos {

    private AuthorizationApiDtos() {
    }

    // ---- Role ----

    public record CreateRoleRequest(
            String code,
            String name,
            Set<String> permissions) {
    }

    public record ReplaceRolePermissionsRequest(Set<String> permissions) {
    }

    public record RoleResponse(
            UUID id,
            String code,
            String name,
            boolean systemRole,
            String status,
            long version,
            Set<String> permissions,
            Instant createdAt,
            Instant updatedAt) {

        static RoleResponse from(RoleRepository.RoleRecord record, Set<String> permissionCodes) {
            return new RoleResponse(
                    record.id(), record.role().code(), record.role().name(),
                    record.systemRole(), "ACTIVE", record.version(),
                    permissionCodes,
                    record.createdAt(), record.updatedAt());
        }
    }

    // ---- Binding ----

    public record CreateBindingRequest(
            String issuer,
            String subjectType,
            String subjectId,
            UUID roleId,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            @Nullable Instant validUntil) {
    }

    public record RevokeBindingRequest(@Nullable String reason) {
    }

    public record CreateSetBindingRequest(
            String setObjectType,
            UUID setObjectId,
            String setRelation,
            UUID setWorkspaceId,
            @Nullable UUID setDirectoryId,
            UUID roleId,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            @Nullable Instant validUntil) {
    }

    public record CreateActingGrantRequest(
            String principalIssuer,
            String principalSubjectId,
            String principalSubjectType,
            UUID agentId,
            String agentVersion,
            java.util.Set<String> permissions,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            @Nullable Instant validUntil) {
    }

    public record ActingGrantResponse(
            UUID id,
            String principalIssuer,
            String principalSubjectId,
            UUID agentId,
            String agentVersion,
            java.util.Set<String> permissions,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            String status,
            Instant validFrom,
            @Nullable Instant validUntil,
            long version) {

        public static ActingGrantResponse from(
                dev.ainer.authorization.application.ActingGrantRepository.PersistedGrant g) {
            Scope scope = g.scope();
            String scopeKind;
            UUID workspaceId = null;
            String resourceType = null;
            UUID resourceId = null;
            switch (scope) {
                case Scope.Workspace ws -> {
                    scopeKind = "WORKSPACE";
                    workspaceId = ws.workspaceId();
                }
                case Scope.Resource res -> {
                    scopeKind = "RESOURCE";
                    workspaceId = res.workspaceId();
                    resourceType = res.resourceType().value();
                    resourceId = res.resourceId();
                }
                default -> scopeKind = "GLOBAL";
            }
            return new ActingGrantResponse(g.id(), g.principal().issuerNamespace(),
                    g.principal().subjectId(), g.agentId(), g.agentVersion(),
                    g.permissions().stream().map(pc -> pc.value())
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                    scopeKind, workspaceId, resourceType, resourceId, g.status().name(),
                    g.validFrom(), g.validUntil(), g.version());
        }
    }

    public record SetBindingResponse(
            UUID id,
            String setObjectType,
            UUID setObjectId,
            String setRelation,
            UUID setWorkspaceId,
            @Nullable UUID setDirectoryId,
            UUID roleId,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            String status,
            Instant validFrom,
            @Nullable Instant validUntil,
            long version) {

        public static SetBindingResponse from(SubjectSetBindingRepository.PersistedSetBinding b) {
            Scope scope = b.scope();
            String scopeKind;
            UUID workspaceId = null;
            String resourceType = null;
            UUID resourceId = null;
            switch (scope) {
                case Scope.Workspace ws -> {
                    scopeKind = "WORKSPACE";
                    workspaceId = ws.workspaceId();
                }
                case Scope.Resource res -> {
                    scopeKind = "RESOURCE";
                    workspaceId = res.workspaceId();
                    resourceType = res.resourceType().value();
                    resourceId = res.resourceId();
                }
                default -> scopeKind = "GLOBAL";
            }
            return new SetBindingResponse(b.id(), b.set().objectType(), b.set().objectId(),
                    b.set().relation(), b.set().workspaceId(), b.set().directoryId(), b.roleId(),
                    scopeKind, workspaceId, resourceType, resourceId, b.status().name(),
                    b.validFrom(), b.validUntil(), b.version());
        }
    }

    public record BindingResponse(
            UUID id,
            String issuer,
            String subjectType,
            String subjectId,
            UUID roleId,
            String roleCode,
            String scopeKind,
            @Nullable UUID workspaceId,
            @Nullable String resourceType,
            @Nullable UUID resourceId,
            String status,
            Instant validFrom,
            @Nullable Instant validUntil,
            long version,
            @Nullable Instant revokedAt,
            @Nullable String revokedReason) {

        static BindingResponse from(SubjectBindingRepository.PersistedBinding pb) {
            return new BindingResponse(
                    pb.id(),
                    pb.subjectRef().issuerNamespace(),
                    pb.subjectRef().type().name(),
                    pb.subjectRef().subjectId(),
                    pb.roleId(),
                    pb.roleCode(),
                    scopeKind(pb.scope()),
                    workspaceId(pb.scope()),
                    resourceType(pb.scope()),
                    resourceId(pb.scope()),
                    pb.status().name(),
                    pb.validFrom(),
                    pb.validUntil(),
                    pb.version(),
                    pb.revokedAt(),
                    pb.revokedReason());
        }

        private static String scopeKind(Scope scope) {
            return switch (scope) {
                case Scope.Global ignored -> "GLOBAL";
                case Scope.Workspace ignored -> "WORKSPACE";
                case Scope.Resource ignored -> "RESOURCE";
            };
        }

        @Nullable
        private static UUID workspaceId(Scope scope) {
            return switch (scope) {
                case Scope.Global ignored -> null;
                case Scope.Workspace ws -> ws.workspaceId();
                case Scope.Resource res -> res.workspaceId();
            };
        }

        @Nullable
        private static String resourceType(Scope scope) {
            return scope instanceof Scope.Resource res ? res.resourceType().value() : null;
        }

        @Nullable
        private static UUID resourceId(Scope scope) {
            return scope instanceof Scope.Resource res ? res.resourceId() : null;
        }
    }

    // ---- Permission ----

    public record PermissionResponse(
            String code,
            String action,
            String resourceType,
            String riskTier,
            String auditLevel,
            boolean systemOnly,
            boolean agentDelegable) {

        static PermissionResponse from(Permission permission) {
            return new PermissionResponse(
                    permission.code().value(),
                    permission.action(),
                    permission.resourceType().value(),
                    permission.riskTier().name(),
                    permission.auditLevel().name(),
                    permission.systemOnly(),
                    permission.agentDelegable());
        }
    }

    // ---- Effective Access ----

    public record EffectiveAccessRequest(
            String issuer,
            String subjectType,
            String subjectId) {
    }

    public record EffectiveAccessResponse(
            String issuer,
            String subjectType,
            String subjectId,
            List<BindingResponse> bindings) {

        static EffectiveAccessResponse from(
                String issuer, String subjectType, String subjectId,
                List<SubjectBindingRepository.PersistedBinding> bindings) {
            return new EffectiveAccessResponse(
                    issuer, subjectType, subjectId,
                    bindings.stream().map(BindingResponse::from).toList());
        }
    }
}
