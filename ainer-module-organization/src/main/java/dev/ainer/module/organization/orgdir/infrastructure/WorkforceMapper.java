package dev.ainer.module.organization.orgdir.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface WorkforceMapper {

    void insertEngagement(EngagementRow row);

    EngagementRow selectEngagement(@Param("directoryId") UUID directoryId,
            @Param("id") UUID id);

    List<EngagementRow> pageEngagements(@Param("directoryId") UUID directoryId,
            @Param("offset") long offset, @Param("limit") int limit);

    long countEngagements(@Param("directoryId") UUID directoryId);

    boolean existsOverlappingEngagement(@Param("directoryId") UUID directoryId,
            @Param("subjectKey") String subjectKey, @Param("validFrom") Instant validFrom,
            @Param("validUntil") Instant validUntil);

    int updateEngagementStatus(@Param("id") UUID id, @Param("status") String status,
            @Param("validUntil") Instant validUntil, @Param("version") long version,
            @Param("now") Instant now);

    void insertUnitAssignment(UnitAssignmentRow row);

    UnitAssignmentRow selectUnitAssignment(@Param("directoryId") UUID directoryId,
            @Param("id") UUID id);

    List<UnitAssignmentRow> selectUnitAssignmentsByEngagement(@Param("engagementId") UUID engagementId);

    int closeUnitAssignment(@Param("id") UUID id, @Param("atTime") Instant atTime,
            @Param("now") Instant now);

    void insertPosition(PositionRow row);

    PositionRow selectPosition(@Param("directoryId") UUID directoryId, @Param("id") UUID id);

    void insertPositionAssignment(PositionAssignmentRow row);

    List<UnitAssignmentRow> selectLiveUnitAssignments(@Param("directoryId") UUID directoryId,
            @Param("orgUnitId") UUID orgUnitId, @Param("atTime") Instant atTime);

    List<PositionAssignmentRow> selectLivePositionAssignments(@Param("directoryId") UUID directoryId,
            @Param("positionId") UUID positionId, @Param("atTime") Instant atTime);

    List<EngagementRow> selectEngagementsByIds(@Param("ids") List<UUID> ids);

    LivePositionAssigneeRow selectLivePositionAssigneeBySubject(
            @Param("positionId") UUID positionId,
            @Param("subjectIssuer") String subjectIssuer,
            @Param("subjectId") String subjectId,
            @Param("atTime") Instant atTime);
}
