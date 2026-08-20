package dev.ainer.module.organization.orgdir.access;

import dev.ainer.authorization.policy.SubjectSetMembership;
import dev.ainer.authorization.policy.SubjectSetMembershipResolver;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;
import dev.ainer.module.organization.orgdir.application.WorkforceRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code workforce.position#assignee} 成员解析器（ADR-0042 O2 首个集合族）。决策时实时
 * 解析：目标岗位存在一条 ENABLED 岗位任职覆盖评估时间，且其父 Engagement 同期覆盖、
 * subject 与 requester 的 issuer/subjectId 完全一致 → MEMBER；validUntil 取父链最早到期。
 * 暂停/终止/撤岗在下次决策即失去成员资格（无事实缓存）。
 *
 * <p>安全语义：查询按 SubjectSetRef 声明的 {@code workspaceId} 过滤目录事实——集合声明的
 * 工作区与岗位实际归属不一致时解析为 NOT_MEMBER，堵住「声明工作区 B + 工作区 A 的岗位」
 * 跨工作区提权通道。
 */
@Component
public class WorkforcePositionMembershipResolver implements SubjectSetMembershipResolver {

    /** 集合族标识：objectType = workforce.position，relation = assignee。 */
    public static final String OBJECT_TYPE = "workforce.position";
    public static final String RELATION = "assignee";

    private final WorkforceRepository workforceRepository;

    public WorkforcePositionMembershipResolver(WorkforceRepository workforceRepository) {
        this.workforceRepository = workforceRepository;
    }

    @Override
    public boolean supports(String objectType, String relation) {
        return OBJECT_TYPE.equals(objectType) && RELATION.equals(relation);
    }

    @Override
    public SubjectSetMembership resolve(SubjectRef requester, SubjectSetRef set, Instant evaluationTime) {
        if (requester.type() != dev.ainer.authorization.domain.SubjectType.USER) {
            return SubjectSetMembership.notMember();
        }
        Optional<WorkforceRepository.LivePositionAssignee> assignee =
                workforceRepository.findLivePositionAssigneeBySubject(
                        set.workspaceId(), set.objectId(), requester.issuerNamespace(),
                        requester.subjectId(), evaluationTime);
        return assignee
                .map(live -> new SubjectSetMembership(
                        SubjectSetMembership.Status.MEMBER,
                        live.validUntil(),
                        "workforce-v1",
                        live.engagementId(),
                        live.positionAssignmentId()))
                .orElseGet(SubjectSetMembership::notMember);
    }
}
