package dev.ainer.module.organization.orgdir.infrastructure;

import dev.ainer.module.organization.orgdir.application.WorkforceRepository;
import dev.ainer.module.organization.orgdir.application.WorkforceRepository.LivePositionAssignee;
import dev.ainer.module.organization.orgdir.domain.AssignmentKind;
import dev.ainer.module.organization.orgdir.domain.EngagementType;
import dev.ainer.module.organization.orgdir.domain.OrgPosition;
import dev.ainer.module.organization.orgdir.domain.OrgStatus;
import dev.ainer.module.organization.orgdir.domain.PositionAssignment;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisWorkforceRepository implements WorkforceRepository {

    private final WorkforceMapper mapper;

    public MybatisWorkforceRepository(WorkforceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertEngagement(WorkforceEngagement engagement) {
        EngagementRow row = new EngagementRow();
        row.setId(engagement.id());
        row.setWorkspaceId(engagement.workspaceId());
        row.setDirectoryId(engagement.directoryId());
        row.setSubjectIssuer(engagement.subjectIssuer());
        row.setSubjectId(engagement.subjectId());
        row.setSubjectType(engagement.subjectType());
        row.setEngagementType(engagement.engagementType().name());
        row.setEmployeeNumber(engagement.employeeNumber());
        row.setValidFrom(engagement.validFrom());
        row.setValidUntil(engagement.validUntil());
        row.setStatus(engagement.status().name());
        row.setVersion(engagement.version());
        row.setCreatedAt(engagement.createdAt());
        row.setUpdatedAt(engagement.updatedAt());
        mapper.insertEngagement(row);
    }

    @Override
    public Optional<WorkforceEngagement> findEngagement(UUID directoryId, UUID engagementId) {
        return Optional.ofNullable(mapper.selectEngagement(directoryId, engagementId))
                .map(MybatisWorkforceRepository::toEngagement);
    }

    @Override
    public List<WorkforceEngagement> pageEngagements(UUID directoryId, long offset, int limit) {
        return mapper.pageEngagements(directoryId, offset, limit).stream()
                .map(MybatisWorkforceRepository::toEngagement).toList();
    }

    @Override
    public long countEngagements(UUID directoryId) {
        return mapper.countEngagements(directoryId);
    }

    @Override
    public boolean existsOverlappingEngagement(
            UUID directoryId, String subjectKey, Instant validFrom, Instant validUntil) {
        return mapper.existsOverlappingEngagement(directoryId, subjectKey, validFrom, validUntil);
    }

    @Override
    public void updateEngagementStatus(
            UUID id, String status, Instant validUntil, long version, Instant now) {
        mapper.updateEngagementStatus(id, status, validUntil, version, now);
    }

    @Override
    public void insertUnitAssignment(UnitAssignment assignment) {
        mapper.insertUnitAssignment(toAssignmentRow(assignment));
    }

    @Override
    public Optional<UnitAssignment> findUnitAssignment(UUID directoryId, UUID assignmentId) {
        return Optional.ofNullable(mapper.selectUnitAssignment(directoryId, assignmentId))
                .map(MybatisWorkforceRepository::toAssignment);
    }

    @Override
    public List<UnitAssignment> findUnitAssignments(UUID engagementId) {
        return mapper.selectUnitAssignmentsByEngagement(engagementId).stream()
                .map(MybatisWorkforceRepository::toAssignment).toList();
    }

    @Override
    public void closeUnitAssignment(UUID id, Instant atTime, Instant now) {
        mapper.closeUnitAssignment(id, atTime, now);
    }

    @Override
    public void insertPosition(OrgPosition position) {
        PositionRow row = new PositionRow();
        row.setId(position.id());
        row.setWorkspaceId(position.workspaceId());
        row.setDirectoryId(position.directoryId());
        row.setOrgUnitId(position.orgUnitId());
        row.setCode(position.code());
        row.setDisplayName(position.displayName());
        row.setStatus(position.status().name());
        row.setVersion(position.version());
        row.setCreatedAt(position.createdAt());
        row.setUpdatedAt(position.updatedAt());
        mapper.insertPosition(row);
    }

    @Override
    public Optional<OrgPosition> findPosition(UUID directoryId, UUID positionId) {
        return Optional.ofNullable(mapper.selectPosition(directoryId, positionId))
                .map(MybatisWorkforceRepository::toPosition);
    }

    @Override
    public void insertPositionAssignment(PositionAssignment assignment) {
        PositionAssignmentRow row = new PositionAssignmentRow();
        row.setId(assignment.id());
        row.setWorkspaceId(assignment.workspaceId());
        row.setDirectoryId(assignment.directoryId());
        row.setPositionId(assignment.positionId());
        row.setEngagementId(assignment.engagementId());
        row.setUnitAssignmentId(assignment.unitAssignmentId());
        row.setOrgUnitId(assignment.orgUnitId());
        row.setKind(assignment.kind().name());
        row.setValidFrom(assignment.validFrom());
        row.setValidUntil(assignment.validUntil());
        row.setStatus(assignment.status().name());
        row.setCreatedAt(assignment.createdAt());
        row.setUpdatedAt(assignment.updatedAt());
        mapper.insertPositionAssignment(row);
    }

    @Override
    public List<UnitAssignment> findLiveUnitAssignments(
            UUID directoryId, UUID orgUnitId, Instant atTime) {
        return mapper.selectLiveUnitAssignments(directoryId, orgUnitId, atTime).stream()
                .map(MybatisWorkforceRepository::toAssignment).toList();
    }

    @Override
    public List<PositionAssignment> findLivePositionAssignments(
            UUID directoryId, UUID positionId, Instant atTime) {
        return mapper.selectLivePositionAssignments(directoryId, positionId, atTime).stream()
                .map(MybatisWorkforceRepository::toPositionAssignment).toList();
    }

    @Override
    public List<WorkforceEngagement> findEngagementsByIds(List<UUID> engagementIds) {
        return mapper.selectEngagementsByIds(engagementIds).stream()
                .map(MybatisWorkforceRepository::toEngagement).toList();
    }

    @Override
    public Optional<LivePositionAssignee> findLivePositionAssigneeBySubject(
            UUID positionId, String subjectIssuer, String subjectId, Instant atTime) {
        LivePositionAssigneeRow row = mapper.selectLivePositionAssigneeBySubject(
                positionId, subjectIssuer, subjectId, atTime);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new LivePositionAssignee(
                row.getPositionAssignmentId(), row.getEngagementId(), row.getValidUntil()));
    }

    private static WorkforceEngagement toEngagement(EngagementRow row) {
        return new WorkforceEngagement(row.getId(), row.getWorkspaceId(), row.getDirectoryId(),
                row.getSubjectIssuer(), row.getSubjectId(), row.getSubjectType(),
                EngagementType.valueOf(row.getEngagementType()), row.getEmployeeNumber(),
                row.getValidFrom(),
                row.getValidUntil(), OrgStatus.valueOf(row.getStatus()), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private static UnitAssignment toAssignment(UnitAssignmentRow row) {
        return new UnitAssignment(row.getId(), row.getWorkspaceId(), row.getDirectoryId(),
                row.getEngagementId(), row.getOrgUnitId(), AssignmentKind.valueOf(row.getKind()),
                row.getValidFrom(), row.getValidUntil(), OrgStatus.valueOf(row.getStatus()),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private static UnitAssignmentRow toAssignmentRow(UnitAssignment assignment) {
        UnitAssignmentRow row = new UnitAssignmentRow();
        row.setId(assignment.id());
        row.setWorkspaceId(assignment.workspaceId());
        row.setDirectoryId(assignment.directoryId());
        row.setEngagementId(assignment.engagementId());
        row.setOrgUnitId(assignment.orgUnitId());
        row.setKind(assignment.kind().name());
        row.setValidFrom(assignment.validFrom());
        row.setValidUntil(assignment.validUntil());
        row.setStatus(assignment.status().name());
        row.setCreatedAt(assignment.createdAt());
        row.setUpdatedAt(assignment.updatedAt());
        return row;
    }

    private static OrgPosition toPosition(PositionRow row) {
        return new OrgPosition(row.getId(), row.getWorkspaceId(), row.getDirectoryId(),
                row.getOrgUnitId(), row.getCode(), row.getDisplayName(),
                OrgStatus.valueOf(row.getStatus()), row.getVersion(), row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static PositionAssignment toPositionAssignment(PositionAssignmentRow row) {
        return new PositionAssignment(row.getId(), row.getWorkspaceId(), row.getDirectoryId(),
                row.getPositionId(), row.getEngagementId(), row.getUnitAssignmentId(),
                row.getOrgUnitId(), AssignmentKind.valueOf(row.getKind()), row.getValidFrom(),
                row.getValidUntil(), OrgStatus.valueOf(row.getStatus()), row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
