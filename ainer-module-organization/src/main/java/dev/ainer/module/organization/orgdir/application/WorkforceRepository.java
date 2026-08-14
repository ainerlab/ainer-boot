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

    void updateEngagementStatus(UUID id, String status, Instant validUntil, long version, Instant now);

    void insertUnitAssignment(UnitAssignment assignment);

    Optional<UnitAssignment> findUnitAssignment(UUID directoryId, UUID assignmentId);

    List<UnitAssignment> findUnitAssignments(UUID engagementId);

    void closeUnitAssignment(UUID id, Instant atTime, Instant now);

    void insertPosition(OrgPosition position);

    Optional<OrgPosition> findPosition(UUID directoryId, UUID positionId);

    void insertPositionAssignment(PositionAssignment assignment);

    List<UnitAssignment> findLiveUnitAssignments(UUID directoryId, UUID orgUnitId, Instant atTime);

    List<PositionAssignment> findLivePositionAssignments(UUID directoryId, UUID positionId, Instant atTime);

    List<WorkforceEngagement> findEngagementsByIds(List<UUID> engagementIds);
}
