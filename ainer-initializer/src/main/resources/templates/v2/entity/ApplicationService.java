package {{package.name}}.{{entity.package}}.application;

import {{package.name}}.{{entity.package}}.infrastructure.{{entity.className}}Mapper;
import {{package.name}}.{{entity.package}}.infrastructure.{{entity.className}}Row;
import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.application.WorkspaceApplicationService;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Workspace 范围应用用例。MyBatis 类型与持久化 Row 不穿透本类，每个 Mapper 操作都显式携带
 * workspaceId。
 */
@Service
public class {{entity.className}}ApplicationService {

    public static final String READ_SCOPE = "{{entity.scope.read}}";
    public static final String WRITE_SCOPE = "{{entity.scope.write}}";

    private final {{entity.className}}Mapper mapper;
    private final WorkspaceApplicationService workspaceService;
    private final {{entity.className}}AccessAuditService accessAudit;
    private final Clock clock;

    public {{entity.className}}ApplicationService(
            {{entity.className}}Mapper mapper,
            WorkspaceApplicationService workspaceService,
            {{entity.className}}AccessAuditService accessAudit,
            Clock clock) {
        this.mapper = mapper;
        this.workspaceService = workspaceService;
        this.accessAudit = accessAudit;
        this.clock = clock;
    }

    @Transactional
    public {{entity.className}}Record create(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            {{entity.className}}Commands.Create command,
            String requestId) {
        requireAccess(principal, workspaceId, null, WRITE_SCOPE, "CREATE", requestId);
        Instant now = clock.instant();
        {{entity.className}}Row row = new {{entity.className}}Row();
        row.setWorkspaceId(workspaceId);
{{entity.applyCreate}}
        row.setCreatedBySubjectId(principal.subjectId());
        row.setUpdatedBySubjectId(principal.subjectId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        UUID id = mapper.insertReturningId(row);
        return requireRecord(workspaceId, id);
    }

    @Transactional(readOnly = true)
    public {{entity.className}}Record get(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            UUID id,
            String requestId) {
        requireAccess(principal, workspaceId, id, READ_SCOPE, "READ", requestId);
        return requireRecord(workspaceId, id);
    }

    @Transactional(readOnly = true)
    public {{entity.className}}Page page(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            int page,
            int size,
            String requestId) {
        requireAccess(principal, workspaceId, null, READ_SCOPE, "PAGE", requestId);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException({{entity.className}}ErrorCode.INVALID_PAGE);
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        var items = mapper.findPage(workspaceId, size, offset).stream()
                .map(this::toRecord)
                .toList();
        return new {{entity.className}}Page(
                items, page, size, mapper.countByWorkspace(workspaceId));
    }

    @Transactional
    public {{entity.className}}Record update(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            UUID id,
            {{entity.className}}Commands.Update command,
            String requestId) {
        requireAccess(principal, workspaceId, id, WRITE_SCOPE, "UPDATE", requestId);
        requireRecord(workspaceId, id);
        {{entity.className}}Row row = new {{entity.className}}Row();
{{entity.applyUpdate}}
        row.setUpdatedBySubjectId(principal.subjectId());
        row.setUpdatedAt(clock.instant());
        if (mapper.updateByWorkspaceAndVersion(workspaceId, id, command.version(), row) != 1) {
            throw new BusinessException({{entity.className}}ErrorCode.CONCURRENT_MODIFICATION);
        }
        return requireRecord(workspaceId, id);
    }

    @Transactional
    public void delete(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            UUID id,
            long version,
            String requestId) {
        requireAccess(principal, workspaceId, id, WRITE_SCOPE, "DELETE", requestId);
        requireRecord(workspaceId, id);
        if (version < 0 || mapper.deleteByWorkspaceAndVersion(workspaceId, id, version) != 1) {
            throw new BusinessException({{entity.className}}ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void requireAccess(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            UUID resourceId,
            String requiredScope,
            String action,
            String requestId) {
        if (!principal.isHuman() || !principal.hasScope(requiredScope)) {
            accessAudit.record(workspaceId, resourceId, principal.subjectId(), action,
                    "DENY", "SCOPE_OR_SUBJECT_DENIED", requestId);
            throw new BusinessException({{entity.className}}ErrorCode.ACCESS_DENIED);
        }
        try {
            // Workspace 是 ACTIVE membership 的权威；只有 scope 不能授予资源访问权。
            workspaceService.get(principal, workspaceId);
        } catch (BusinessException denied) {
            accessAudit.record(workspaceId, resourceId, principal.subjectId(), action,
                    "DENY", denied.errorCode().code(), requestId);
            throw denied;
        }
        accessAudit.record(workspaceId, resourceId, principal.subjectId(), action,
                "ALLOW", "AUTHORIZED", requestId);
    }

    private {{entity.className}}Record requireRecord(UUID workspaceId, UUID id) {
        {{entity.className}}Row row = mapper.findByWorkspaceAndId(workspaceId, id);
        if (row == null) {
            throw new BusinessException({{entity.className}}ErrorCode.NOT_FOUND);
        }
        return toRecord(row);
    }

    private {{entity.className}}Record toRecord({{entity.className}}Row row) {
        return new {{entity.className}}Record(
                row.getId(),
                row.getWorkspaceId(),
{{entity.responseValues}},
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
