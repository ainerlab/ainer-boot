package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;

/**
 * 聚合 {@link SubjectSetMembershipResolver} 提供者的注册表。未知集合族不受支持
 * （fail-closed）；默认聚合返回 {@code UNAVAILABLE} 成员关系。
 */
public interface SubjectSetMembershipRegistry {

    boolean supports(SubjectSetRef set);

    SubjectSetMembership membership(SubjectRef requester, SubjectSetRef set, Instant evaluationTime);
}
