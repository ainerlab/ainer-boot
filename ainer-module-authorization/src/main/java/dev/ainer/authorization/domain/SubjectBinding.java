package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 在有效时间窗口内为主体分配 {@link Role} 与精确 {@link Scope}（ADR-0030 §4.1）。
 * Binding 可撤销；仍然有效的 JWT 无法恢复已撤销的数据库授权。
 */
public record SubjectBinding(
        SubjectRef subject,
        Role role,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version) {

    public SubjectBinding {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        // validUntil 可为 null（开放式，无截止时间）。
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && role.grants(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
