package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 在有效时间窗口内为 {@link SubjectSetRef} 分配 {@link Role} 与精确 {@link Scope}
 * （ADR-0042 O2）。与直接 {@link SubjectBinding} 共享 Role/Scope/时间/撤销语义；
 * 请求主体只有通过决策时的集合成员关系才获得授权。GLOBAL scope 与 system-only/HIGH
 * 风险权限在创建时即被拒绝——引擎另外也绝不经集合 Binding 提供 GLOBAL。
 */
public record SubjectSetBinding(
        UUID id,
        SubjectSetRef set,
        Role role,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version) {

    public SubjectSetBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        if (scope instanceof Scope.Global) {
            throw new IllegalArgumentException("SubjectSetBinding must not use a GLOBAL scope");
        }
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && role.grants(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
