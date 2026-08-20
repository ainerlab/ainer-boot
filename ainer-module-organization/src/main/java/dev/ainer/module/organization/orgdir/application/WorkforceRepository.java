package dev.ainer.module.organization.orgdir.application;

import dev.ainer.module.organization.orgdir.domain.OrgPosition;
import dev.ainer.module.organization.orgdir.domain.PositionAssignment;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 任职、分配与岗位持久化端口。 */
public interface WorkforceRepository {

    void insertEngagement(WorkforceEngagement engagement);

    Optional<WorkforceEngagement> findEngagement(UUID directoryId, UUID engagementId);

    List<WorkforceEngagement> pageEngagements(UUID directoryId, long offset, int limit);

    long countEngagements(UUID directoryId);

    boolean existsOverlappingEngagement(UUID directoryId, String subjectKey, Instant validFrom, Instant validUntil);

    boolean updateEngagementStatus(UUID id, String status, Instant validUntil, long version, Instant now);

    void insertUnitAssignment(UnitAssignment assignment);

    Optional<UnitAssignment> findUnitAssignment(UUID directoryId, UUID assignmentId);

    List<UnitAssignment> findUnitAssignments(UUID engagementId);

    boolean closeUnitAssignment(UUID id, Instant atTime, Instant now);

    void insertPosition(OrgPosition position);

    Optional<OrgPosition> findPosition(UUID directoryId, UUID positionId);

    void insertPositionAssignment(PositionAssignment assignment);

    List<UnitAssignment> findLiveUnitAssignments(UUID directoryId, UUID orgUnitId, Instant atTime);

    List<PositionAssignment> findLivePositionAssignments(UUID directoryId, UUID positionId, Instant atTime);

    List<WorkforceEngagement> findEngagementsByIds(List<UUID> engagementIds);

    /** 岗位在岗事实投影：position assignment + 父 engagement 同时覆盖评估时间的活体行。 */
    record LivePositionAssignee(
            UUID positionAssignmentId,
            UUID engagementId,
            Instant validUntil) {
    }

    /**
     * 决策时实时解析：岗位 positionId 当前是否由 subject（issuer+subjectId）在岗，
     * 且父 Engagement ENABLED 并覆盖评估时间。validUntil 取岗位任职/任职关系最早到期。
     */
    Optional<LivePositionAssignee> findLivePositionAssigneeBySubject(
            UUID workspaceId, UUID positionId, String subjectIssuer, String subjectId, Instant atTime);
}
