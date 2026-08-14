package dev.ainer.module.organization.orgdir.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.organization.orgdir.domain.AssignmentKind;
import dev.ainer.module.organization.orgdir.domain.EngagementType;
import dev.ainer.module.organization.orgdir.domain.OrgChangeAudit;
import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgPosition;
import dev.ainer.module.organization.orgdir.domain.OrgStatus;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.PositionAssignment;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 任职/分配/岗位应用服务（ADR-0042 O1）。不变量：同目录同 Subject 任职期不重叠（数据库
 * EXCLUDE + 服务层预检）；子分配有效期包含于父任职；同期唯一未闭合 PRIMARY；调岗单事务
 * 同时关闭旧分配并创建新分配；成员/岗位投影按评估时间实时解析（无事实缓存）。
 */
@Service
public class WorkforceApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.regex.Pattern SAFE_SUBJECT =
            java.util.regex.Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final WorkforceRepository repository;
    private final DirectoryRepository directoryRepository;
    private final OrgChangeAuditRepository auditRepository;
    private final OrganizationProperties properties;
    private final Clock clock;

    public WorkforceApplicationService(
            WorkforceRepository repository,
            DirectoryRepository directoryRepository,
            OrgChangeAuditRepository auditRepository,
            OrganizationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.directoryRepository = directoryRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public WorkforceEngagement engage(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            String subjectIssuer,
            String subjectId,
            String engagementType,
            String employeeNumber,
            Instant validFrom,
            Instant validUntil) {
        requireManage(principal);
        OrgDirectory directory = requireDirectory(directoryId);
        requirePeriod(validFrom, validUntil);
        String issuer = subjectIssuer == null ? "" : subjectIssuer.strip();
        if (properties.trustedIssuer().isEmpty() || !properties.trustedIssuer().equals(issuer)) {
            throw new BusinessException(OrganizationErrorCode.INVALID_ISSUER);
        }
        String subject = subjectId == null ? "" : subjectId.strip();
        if (!SAFE_SUBJECT.matcher(subject).matches()) {
            throw new BusinessException(OrganizationErrorCode.INVALID_SUBJECT);
        }
        if (employeeNumber != null && employeeNumber.isBlank()) {
            employeeNumber = null;
        }
        EngagementType parsedType = parseEngagementType(engagementType);
        String subjectKey = issuer + ":" + subject;
        if (repository.existsOverlappingEngagement(directoryId, subjectKey, validFrom, validUntil)) {
            throw new BusinessException(OrganizationErrorCode.ENGAGEMENT_PERIOD_OVERLAP);
        }
        Instant now = clock.instant();
        WorkforceEngagement engagement = new WorkforceEngagement(
                Uuidv7.generate(), directory.workspaceId(), directoryId,
                issuer, subject, "USER", parsedType, employeeNumber,
                validFrom, validUntil, OrgStatus.ENABLED, 1L, now, now);
        try {
            repository.insertEngagement(engagement);
        } catch (DuplicateKeyException duplicate) {
            if (duplicate.getMessage() != null && duplicate.getMessage().contains("employee_number")) {
                throw new BusinessException(OrganizationErrorCode.DUPLICATE_EMPLOYEE_NUMBER);
            }
            throw new BusinessException(OrganizationErrorCode.ENGAGEMENT_PERIOD_OVERLAP);
        }
        audit(principal, requestId, "ENGAGEMENT", engagement.id(), "ENGAGED", now);
        return engagement;
    }

    public WorkforceEngagement getEngagement(AuthenticatedPrincipal principal, UUID directoryId, UUID id) {
        requireRead(principal);
        return repository.findEngagement(directoryId, id)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.ENGAGEMENT_NOT_FOUND));
    }

    public UnitAssignment getUnitAssignment(
            AuthenticatedPrincipal principal, UUID directoryId, UUID assignmentId) {
        requireRead(principal);
        return repository.findUnitAssignment(directoryId, assignmentId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.ASSIGNMENT_NOT_FOUND));
    }

    public List<WorkforceEngagement> pageEngagements(
            AuthenticatedPrincipal principal, UUID directoryId, long page, long size) {
        requireRead(principal);
        long safePage = Math.max(page, 1);
        int safeSize = (int) Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return repository.pageEngagements(directoryId, (safePage - 1) * safeSize, safeSize);
    }

    public long countEngagements(AuthenticatedPrincipal principal, UUID directoryId) {
        requireRead(principal);
        return repository.countEngagements(directoryId);
    }

    @Transactional
    public WorkforceEngagement suspendEngagement(
            AuthenticatedPrincipal principal, String requestId, UUID directoryId, UUID engagementId) {
        requireManage(principal);
        WorkforceEngagement engagement = requireEngagement(directoryId, engagementId);
        if (engagement.status() != OrgStatus.ENABLED) {
            throw new BusinessException(OrganizationErrorCode.INVALID_STATUS_CHANGE);
        }
        Instant now = clock.instant();
        repository.updateEngagementStatus(
                engagementId, OrgStatus.SUSPENDED.name(), engagement.validUntil(),
                engagement.version() + 1, now);
        audit(principal, requestId, "ENGAGEMENT", engagementId, "SUSPENDED", now);
        return repository.findEngagement(directoryId, engagementId).orElseThrow();
    }

    @Transactional
    public WorkforceEngagement terminateEngagement(
            AuthenticatedPrincipal principal, String requestId, UUID directoryId, UUID engagementId) {
        requireManage(principal);
        WorkforceEngagement engagement = requireEngagement(directoryId, engagementId);
        if (engagement.status() == OrgStatus.REVOKED) {
            throw new BusinessException(OrganizationErrorCode.INVALID_STATUS_CHANGE);
        }
        Instant now = clock.instant();
        Instant closedUntil = engagement.validUntil() == null || engagement.validUntil().isAfter(now)
                ? now : engagement.validUntil();
        repository.updateEngagementStatus(
                engagementId, OrgStatus.REVOKED.name(), closedUntil,
                engagement.version() + 1, now);
        audit(principal, requestId, "ENGAGEMENT", engagementId, "TERMINATED", now);
        return repository.findEngagement(directoryId, engagementId).orElseThrow();
    }

    @Transactional
    public UnitAssignment assignUnit(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            UUID engagementId,
            UUID orgUnitId,
            AssignmentKind kind,
            Instant validFrom,
            Instant validUntil) {
        requireManage(principal);
        WorkforceEngagement engagement = requireEngagement(directoryId, engagementId);
        OrgUnit unit = directoryRepository.findUnit(directoryId, orgUnitId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.UNIT_NOT_FOUND));
        requirePeriod(validFrom, validUntil);
        requireWithin(validFrom, validUntil, engagement.validFrom(), engagement.validUntil());
        if (unit.status() != OrgStatus.ENABLED) {
            throw new BusinessException(OrganizationErrorCode.UNIT_NOT_FOUND);
        }
        Instant now = clock.instant();
        UnitAssignment assignment = new UnitAssignment(
                Uuidv7.generate(), engagement.workspaceId(), directoryId, engagementId, orgUnitId,
                kind, validFrom, validUntil, OrgStatus.ENABLED, now, now);
        try {
            repository.insertUnitAssignment(assignment);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(OrganizationErrorCode.OPEN_PRIMARY_CONFLICT);
        }
        audit(principal, requestId, "UNIT_ASSIGNMENT", assignment.id(), "ASSIGNED", now);
        return assignment;
    }

    @Transactional
    public UnitAssignment transferUnitAssignment(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            UUID engagementId,
            UUID assignmentId,
            UUID targetUnitId,
            Instant atTime) {
        requireManage(principal);
        Objects.requireNonNull(atTime, "atTime");
        WorkforceEngagement engagement = requireEngagement(directoryId, engagementId);
        UnitAssignment existing = repository.findUnitAssignment(directoryId, assignmentId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.ASSIGNMENT_NOT_FOUND));
        if (!existing.engagementId().equals(engagementId)) {
            throw new BusinessException(OrganizationErrorCode.ASSIGNMENT_NOT_FOUND);
        }
        if (existing.kind() != AssignmentKind.PRIMARY
                || existing.validUntil() != null
                || existing.status() != OrgStatus.ENABLED) {
            throw new BusinessException(OrganizationErrorCode.INVALID_STATUS_CHANGE);
        }
        if (!atTime.isAfter(existing.validFrom())) {
            throw new BusinessException(OrganizationErrorCode.INVALID_PERIOD);
        }
        OrgUnit target = directoryRepository.findUnit(directoryId, targetUnitId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.UNIT_NOT_FOUND));
        requireWithin(atTime, null, engagement.validFrom(), engagement.validUntil());
        Instant now = clock.instant();
        repository.closeUnitAssignment(assignmentId, atTime, now);
        UnitAssignment next = new UnitAssignment(
                Uuidv7.generate(), engagement.workspaceId(), directoryId, engagementId, targetUnitId,
                AssignmentKind.PRIMARY, atTime, null, OrgStatus.ENABLED, now, now);
        try {
            repository.insertUnitAssignment(next);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(OrganizationErrorCode.OPEN_PRIMARY_CONFLICT);
        }
        audit(principal, requestId, "UNIT_ASSIGNMENT", assignmentId, "TRANSFERRED_OUT", now);
        audit(principal, requestId, "UNIT_ASSIGNMENT", next.id(), "TRANSFERRED_IN", now);
        return next;
    }

    @Transactional
    public OrgPosition createPosition(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            UUID orgUnitId,
            String code,
            String displayName) {
        requireManage(principal);
        requireText(code);
        requireText(displayName);
        OrgUnit unit = directoryRepository.findUnit(directoryId, orgUnitId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.UNIT_NOT_FOUND));
        Instant now = clock.instant();
        OrgPosition position = new OrgPosition(Uuidv7.generate(), unit.workspaceId(), directoryId,
                orgUnitId, code.strip(), displayName.strip(), OrgStatus.ENABLED, 1L, now, now);
        try {
            repository.insertPosition(position);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_UNIT_CODE);
        }
        audit(principal, requestId, "POSITION", position.id(), "CREATED", now);
        return position;
    }

    @Transactional
    public PositionAssignment assignPosition(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            UUID positionId,
            UUID engagementId,
            UUID unitAssignmentId,
            AssignmentKind kind,
            Instant validFrom,
            Instant validUntil) {
        requireManage(principal);
        OrgPosition position = repository.findPosition(directoryId, positionId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.POSITION_NOT_FOUND));
        WorkforceEngagement engagement = requireEngagement(directoryId, engagementId);
        UnitAssignment unitAssignment = repository.findUnitAssignment(directoryId, unitAssignmentId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.ASSIGNMENT_NOT_FOUND));
        if (!unitAssignment.engagementId().equals(engagementId)
                || !unitAssignment.orgUnitId().equals(position.orgUnitId())) {
            throw new BusinessException(OrganizationErrorCode.UNIT_MISMATCH);
        }
        requirePeriod(validFrom, validUntil);
        requireWithin(validFrom, validUntil, engagement.validFrom(), engagement.validUntil());
        requireWithin(validFrom, validUntil, unitAssignment.validFrom(), unitAssignment.validUntil());
        Instant now = clock.instant();
        PositionAssignment assignment = new PositionAssignment(
                Uuidv7.generate(), engagement.workspaceId(), directoryId,
                positionId, engagementId, unitAssignmentId, unitAssignment.orgUnitId(), kind,
                validFrom, validUntil, OrgStatus.ENABLED, now, now);
        repository.insertPositionAssignment(assignment);
        audit(principal, requestId, "POSITION_ASSIGNMENT", assignment.id(), "ASSIGNED", now);
        return assignment;
    }

    /** Unit 成员投影：决策时实时解析父链（Engagement + UnitAssignment 同时覆盖评估时间）。 */
    public List<UnitAssignment> unitMembers(
            AuthenticatedPrincipal principal, UUID directoryId, UUID orgUnitId, Instant atTime) {
        requireRead(principal);
        directoryRepository.findDirectory(directoryId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.DIRECTORY_NOT_FOUND));
        Instant evaluationTime = atTime == null ? clock.instant() : atTime;
        return repository.findLiveUnitAssignments(directoryId, orgUnitId, evaluationTime);
    }

    /** 岗位在岗者投影：同样按评估时间实时解析。 */
    public List<PositionAssignment> positionAssignees(
            AuthenticatedPrincipal principal, UUID directoryId, UUID positionId, Instant atTime) {
        requireRead(principal);
        repository.findPosition(directoryId, positionId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.POSITION_NOT_FOUND));
        Instant evaluationTime = atTime == null ? clock.instant() : atTime;
        return repository.findLivePositionAssignments(directoryId, positionId, evaluationTime);
    }

    public List<WorkforceEngagement> engagementsForMembers(
            AuthenticatedPrincipal principal, List<UUID> engagementIds) {
        requireRead(principal);
        if (engagementIds.isEmpty()) {
            return List.of();
        }
        return repository.findEngagementsByIds(engagementIds);
    }

    private static EngagementType parseEngagementType(String engagementType) {
        try {
            return EngagementType.valueOf(engagementType);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST, "engagementType 不合法");
        }
    }

    private OrgDirectory requireDirectory(UUID directoryId) {
        return directoryRepository.findDirectory(directoryId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.DIRECTORY_NOT_FOUND));
    }

    private WorkforceEngagement requireEngagement(UUID directoryId, UUID engagementId) {
        return repository.findEngagement(directoryId, engagementId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.ENGAGEMENT_NOT_FOUND));
    }

    private static void requirePeriod(Instant validFrom, Instant validUntil) {
        Objects.requireNonNull(validFrom, "validFrom");
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new BusinessException(OrganizationErrorCode.INVALID_PERIOD);
        }
    }

    /**
     * 子关系起点不得早于父关系起点；显式终点不得晚于父终点。开放子关系（until=null）允许：
     * 其收敛由成员/岗位投影的评估时父链检查保证（ADR-0042 §3），不会越出父任职期生效。
     */
    private static void requireWithin(
            Instant from, Instant until, Instant parentFrom, Instant parentUntil) {
        if (from.isBefore(parentFrom)) {
            throw new BusinessException(OrganizationErrorCode.INVALID_PERIOD);
        }
        if (parentUntil != null && !from.isBefore(parentUntil)) {
            throw new BusinessException(OrganizationErrorCode.INVALID_PERIOD);
        }
        if (until != null && parentUntil != null && until.isAfter(parentUntil)) {
            throw new BusinessException(OrganizationErrorCode.INVALID_PERIOD);
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
    }

    private void audit(
            AuthenticatedPrincipal principal,
            String requestId,
            String entityType,
            UUID entityId,
            String operation,
            Instant now) {
        auditRepository.insert(new OrgChangeAudit(
                Uuidv7.generate(), entityType, entityId, operation,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(), requestId, now));
    }

    private static void requireManage(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(OrganizationAuthorities.MANAGE)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireRead(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(OrganizationAuthorities.READ)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
