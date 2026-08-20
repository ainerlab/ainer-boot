package dev.ainer.authorization.policy;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 决策时对单个请求者与单个 {@link dev.ainer.authorization.domain.SubjectSetRef} 的成员
 * 关系求值结果（ADR-0042 §3 pull 式解析）。{@code UNAVAILABLE} 是 fail-closed：
 * 绝不授予。
 */
public record SubjectSetMembership(
        Status status,
        @Nullable Instant validUntil,
        @Nullable String factVersion,
        @Nullable UUID engagementId,
        @Nullable UUID positionAssignmentId) {

    public enum Status {
        MEMBER,
        NOT_MEMBER,
        UNAVAILABLE
    }

    public static SubjectSetMembership notMember() {
        return new SubjectSetMembership(Status.NOT_MEMBER, null, null, null, null);
    }

    public static SubjectSetMembership unavailable() {
        return new SubjectSetMembership(Status.UNAVAILABLE, null, null, null, null);
    }

    public boolean isMember() {
        return status == Status.MEMBER;
    }
}
