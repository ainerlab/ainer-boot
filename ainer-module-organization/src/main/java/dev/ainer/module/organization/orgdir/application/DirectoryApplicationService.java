package dev.ainer.module.organization.orgdir.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.organization.orgdir.domain.OrgChangeAudit;
import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgStatus;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.OrgUnitKind;
import dev.ainer.module.organization.orgdir.domain.OrgUnitParent;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 目录与组织单元应用服务（ADR-0042 O1）。创建目录原子建立 ROOT Unit；单元创建校验父单元、
 * 目录归属与状态；所有写入同事务追加变更审计。
 */
@Service
public class DirectoryApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DirectoryRepository directoryRepository;
    private final OrgChangeAuditRepository auditRepository;
    private final Clock clock;

    public DirectoryApplicationService(
            DirectoryRepository directoryRepository,
            OrgChangeAuditRepository auditRepository,
            Clock clock) {
        this.directoryRepository = directoryRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    @Transactional
    public OrgDirectory createDirectory(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID workspaceId,
            String code,
            String displayName) {
        requireManage(principal);
        Objects.requireNonNull(workspaceId, "workspaceId");
        requireText(code, "code");
        requireText(displayName, "displayName");
        if (code.length() > 64 || displayName.length() > 128) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.instant();
        UUID directoryId = Uuidv7.generate();
        OrgDirectory directory = new OrgDirectory(
                directoryId, workspaceId, code.strip(), displayName.strip(),
                OrgStatus.ENABLED, 1L, now, now);
        try {
            directoryRepository.insertDirectory(directory);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_DIRECTORY_CODE);
        }
        OrgUnit root = new OrgUnit(Uuidv7.generate(), workspaceId, directoryId,
                "ROOT", displayName.strip() + " 根单元", OrgUnitKind.ROOT,
                OrgStatus.ENABLED, 1L, now, now);
        directoryRepository.insertUnit(root);
        audit(principal, requestId, "DIRECTORY", directoryId, "CREATED", now);
        audit(principal, requestId, "UNIT", root.id(), "CREATED", now);
        return directory;
    }

    public OrgDirectory getDirectory(AuthenticatedPrincipal principal, UUID workspaceId, UUID id) {
        requireRead(principal);
        return directoryRepository.findDirectory(workspaceId, id)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.DIRECTORY_NOT_FOUND));
    }

    public List<OrgDirectory> pageDirectories(
            AuthenticatedPrincipal principal, UUID workspaceId, long page, long size) {
        requireRead(principal);
        long safePage = Math.max(page, 1);
        int safeSize = (int) Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return directoryRepository.pageDirectories(workspaceId, (safePage - 1) * safeSize, safeSize);
    }

    public long countDirectories(AuthenticatedPrincipal principal, UUID workspaceId) {
        requireRead(principal);
        return directoryRepository.countDirectories(workspaceId);
    }

    @Transactional
    public OrgUnit createUnit(
            AuthenticatedPrincipal principal,
            String requestId,
            UUID directoryId,
            UUID parentUnitId,
            String code,
            String displayName) {
        requireManage(principal);
        requireText(code, "code");
        requireText(displayName, "displayName");
        OrgDirectory directory = directoryRepository.findDirectory(directoryId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.DIRECTORY_NOT_FOUND));
        requireEnabled(directory.status(), OrganizationErrorCode.DIRECTORY_NOT_FOUND);
        OrgUnit parent = directoryRepository.findUnit(directoryId, parentUnitId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.UNIT_NOT_FOUND));
        requireEnabled(parent.status(), OrganizationErrorCode.UNIT_NOT_FOUND);
        if (parent.kind() == OrgUnitKind.ROOT && !"ROOT".equals(code)) {
            // ROOT 子单元没有额外限制；保留分支以显式表达根的直接子级也是普通 UNIT
        }
        Instant now = clock.instant();
        OrgUnit unit = new OrgUnit(Uuidv7.generate(), directory.workspaceId(), directoryId,
                code.strip(), displayName.strip(), OrgUnitKind.UNIT,
                OrgStatus.ENABLED, 1L, now, now);
        try {
            directoryRepository.insertUnit(unit);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_UNIT_CODE);
        }
        directoryRepository.insertUnitParent(new OrgUnitParent(
                Uuidv7.generate(), directory.workspaceId(), directoryId,
                unit.id(), parent.id(), now, null, OrgStatus.ENABLED.name(), now, now));
        audit(principal, requestId, "UNIT", unit.id(), "CREATED", now);
        return unit;
    }

    public List<OrgUnit> unitTree(AuthenticatedPrincipal principal, UUID directoryId) {
        requireRead(principal);
        directoryRepository.findDirectory(directoryId)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.DIRECTORY_NOT_FOUND));
        return directoryRepository.findUnits(directoryId);
    }

    private static void requireEnabled(OrgStatus status, OrganizationErrorCode notFound) {
        if (status != OrgStatus.ENABLED) {
            throw new BusinessException(notFound);
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST, field + " 不能为空");
        }
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
