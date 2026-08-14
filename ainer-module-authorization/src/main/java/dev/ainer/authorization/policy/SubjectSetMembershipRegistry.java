package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;

/**
 * Aggregating registry over {@link SubjectSetMembershipResolver} providers. Unknown families are
 * unsupported (fail-closed); the default aggregation returns {@code UNAVAILABLE} membership.
 */
public interface SubjectSetMembershipRegistry {

    boolean supports(SubjectSetRef set);

    SubjectSetMembership membership(SubjectRef requester, SubjectSetRef set, Instant evaluationTime);
}
