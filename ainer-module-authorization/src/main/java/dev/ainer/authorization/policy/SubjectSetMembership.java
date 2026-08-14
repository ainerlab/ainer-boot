package dev.ainer.authorization.policy;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision-time membership evaluation result for one requester against one
 * {@link dev.ainer.authorization.domain.SubjectSetRef}（ADR-0042 §3 pull-based resolution）。
 * {@code UNAVAILABLE} is fail-closed: it never grants.
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
