package dev.ainer.module.organization.orgdir.api;

import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgPosition;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.PositionAssignment;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;

import java.time.Instant;
import java.util.UUID;

/** 组织目录 API DTO（ADR-0042 O1）。 */
public final class OrganizationApiDtos {

    private OrganizationApiDtos() {
    }

    public record CreateDirectoryRequest(UUID workspaceId, String code, String displayName) {
    }

    public record CreateUnitRequest(UUID parentUnitId, String code, String displayName) {
    }

    public record EngageRequest(
            String subjectIssuer,
            String subjectId,
            String engagementType,
            String employeeNumber,
            Instant validFrom,
            Instant validUntil) {
    }

    public record AssignUnitRequest(
            UUID engagementId, UUID orgUnitId, String kind, Instant validFrom, Instant validUntil) {
    }

    public record TransferRequest(UUID engagementId, UUID targetUnitId, Instant atTime) {
    }

    public record CreatePositionRequest(UUID orgUnitId, String code, String displayName) {
    }

    public record AssignPositionRequest(
            UUID positionId, UUID engagementId, UUID unitAssignmentId, String kind,
            Instant validFrom, Instant validUntil) {
    }

    public record DirectoryResponse(
            UUID id, UUID workspaceId, String code, String displayName, String status,
            long version, Instant createdAt, Instant updatedAt) {

        public static DirectoryResponse from(OrgDirectory d) {
            return new DirectoryResponse(d.id(), d.workspaceId(), d.code(), d.displayName(),
                    d.status().name(), d.version(), d.createdAt(), d.updatedAt());
        }
    }

    public record UnitResponse(
            UUID id, UUID directoryId, String code, String displayName, String kind,
            String status, long version, Instant createdAt) {

        public static UnitResponse from(OrgUnit u) {
            return new UnitResponse(u.id(), u.directoryId(), u.code(), u.displayName(),
                    u.kind().name(), u.status().name(), u.version(), u.createdAt());
        }
    }

    public record EngagementResponse(
            UUID id, UUID directoryId, String subjectIssuer, String subjectId,
            String engagementType, String employeeNumber, Instant validFrom, Instant validUntil,
            String status, long version) {

        public static EngagementResponse from(WorkforceEngagement e) {
            return new EngagementResponse(e.id(), e.directoryId(), e.subjectIssuer(),
                    e.subjectId(), e.engagementType().name(), e.employeeNumber(), e.validFrom(),
                    e.validUntil(), e.status().name(), e.version());
        }
    }

    public record UnitAssignmentResponse(
            UUID id, UUID engagementId, UUID orgUnitId, String kind, Instant validFrom,
            Instant validUntil, String status) {

        public static UnitAssignmentResponse from(UnitAssignment a) {
            return new UnitAssignmentResponse(a.id(), a.engagementId(), a.orgUnitId(),
                    a.kind().name(), a.validFrom(), a.validUntil(), a.status().name());
        }
    }

    public record PositionResponse(
            UUID id, UUID orgUnitId, String code, String displayName, String status) {

        public static PositionResponse from(OrgPosition p) {
            return new PositionResponse(p.id(), p.orgUnitId(), p.code(), p.displayName(),
                    p.status().name());
        }
    }

    public record PositionAssignmentResponse(
            UUID id, UUID positionId, UUID engagementId, UUID unitAssignmentId, String kind,
            Instant validFrom, Instant validUntil, String status) {

        public static PositionAssignmentResponse from(PositionAssignment a) {
            return new PositionAssignmentResponse(a.id(), a.positionId(), a.engagementId(),
                    a.unitAssignmentId(), a.kind().name(), a.validFrom(), a.validUntil(),
                    a.status().name());
        }
    }

    public record UnitMemberResponse(
            UnitAssignmentResponse assignment, String subjectIssuer, String subjectId,
            String engagementStatus) {

        public static UnitMemberResponse from(UnitAssignment assignment, WorkforceEngagement e) {
            return new UnitMemberResponse(UnitAssignmentResponse.from(assignment),
                    e.subjectIssuer(), e.subjectId(), e.status().name());
        }
    }

    public record PageResponse<T>(java.util.List<T> records, long total, long page, long size) {
    }
}
