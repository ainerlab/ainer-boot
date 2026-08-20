package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.ErrorCode;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Workspace 授权审计的写入服务，记录每次授权检查的 ALLOWED/DENIED 决策。
 *
 * <p>写入使用 {@code REQUIRES_NEW} 独立事务：被拒绝的操作随后抛出异常并回滚业务事务时，
 * DENY 审计必须仍然落库。审计内容只包含稳定标识（操作者、目标主体、动作、决策、reason
 * code），不保存 Token、prompt 或资源正文。
 */
@Service
public class WorkspaceAuthorizationAuditService {

    private final WorkspaceAuthorizationAuditRepository repository;
    private final Clock clock;

    public WorkspaceAuthorizationAuditService(
            WorkspaceAuthorizationAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action,
            WorkspaceAuthorizationDecision decision,
            ErrorCode reason) {
        repository.insert(new WorkspaceAuthorizationAudit(
                dev.ainer.core.uuid.Uuidv7.generate(),
                workspaceId,
                principal.subjectId(),
                targetSubjectId,
                action,
                decision,
                reason.code(),
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public WorkspaceAuthorizationAuditPage findPage(
            UUID workspaceId, int page, int size, long offset) {
        return repository.findPage(workspaceId, page, size, offset);
    }
}
