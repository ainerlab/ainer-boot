package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 单层 principal→agent 委托（ADR-0043 A1）。授权携带显式的最小权限子集与单个结构化
 * scope，绝不扩大 principal 的权限——每个检查点都重新解析 principal 的 live Binding 与
 * agent 状态（pull 式，无缓存）。授权在构造上不可再委托；GLOBAL 无法表达。
 */
public record ActingGrant(
        UUID id,
        SubjectRef principal,
        UUID agentId,
        String agentVersion,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version,
        Set<PermissionCode> permissions) {

    public ActingGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agentVersion, "agentVersion");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(permissions, "permissions");
        if (scope instanceof Scope.Global) {
            throw new IllegalArgumentException("ActingGrant must not use a GLOBAL scope");
        }
        permissions = Set.copyOf(permissions);
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && permissions.contains(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
