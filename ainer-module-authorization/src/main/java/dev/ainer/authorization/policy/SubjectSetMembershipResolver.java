package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;

/**
 * Product-provided membership source for one subject-set family (ADR-0042 O2). Implementations
 * are pull-based: membership is recomputed at decision time from authoritative facts (live
 * engagement + assignment periods), never cached, so suspensions and terminations take effect on
 * the next decision. The authorization core never depends on an implementation.
 */
public interface SubjectSetMembershipResolver {

    /** Whether this resolver answers sets of the given objectType/relation family. */
    boolean supports(String objectType, String relation);

    /**
     * Evaluate requester membership at {@code evaluationTime}. Must not throw for unknown
     * objects; return {@code UNAVAILABLE} when the owning facts cannot be read.
     */
    SubjectSetMembership resolve(SubjectRef requester, SubjectSetRef set, Instant evaluationTime);
}
