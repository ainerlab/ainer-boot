package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link dev.ainer.authorization.domain.SubjectBinding} 生命周期管理的应用用例
 * （ADR-0030 S1）。Binding 在有效时间窗口内为主体分配持久化 Role 与结构化 Scope。
 * 撤销是逻辑状态迁移——仍然有效的 JWT 无法恢复已撤销的数据库授权。
 *
 * <p>管理变更通过 {@link AuthorizationChangeAuditService} 在同一事务内审计
 * （ADR-0030 §11.7）。
 */
@Service
@Transactional
public class SubjectBindingApplicationService {

    static final String TARGET_TYPE_BINDING = "BINDING";

    private final SubjectBindingRepository bindingRepository;
    private final RoleRepository roleRepository;
    private final GrantAdministrationGuard administrationGuard;
    private final AuthorizationChangeAuditService changeAuditService;
    private final Clock clock;

    public SubjectBindingApplicationService(
            SubjectBindingRepository bindingRepository,
            RoleRepository roleRepository,
            GrantAdministrationGuard administrationGuard,
            AuthorizationChangeAuditService changeAuditService,
            Clock clock) {
        this.bindingRepository = bindingRepository;
        this.roleRepository = roleRepository;
        this.administrationGuard = administrationGuard;
        this.changeAuditService = changeAuditService;
        this.clock = clock;
    }

    /**
     * 创建新 Binding。
     *
     * @throws BusinessException 当管理者/目标/scope/Role 权限不可分配，或 Role 不存在时。
     */
    public UUID createBinding(
            AuthenticatedPrincipal actor, SubjectRef subject, UUID roleId, Scope scope,
            Instant validFrom, @Nullable Instant validUntil,
            @Nullable String requestId, @Nullable String traceId) {
        administrationGuard.requireManager(actor);
        RoleRepository.RoleRecord role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
        administrationGuard.requireBindingCreation(actor, subject, role, scope);
        UUID bindingId = bindingRepository.save(subject, roleId, scope, validFrom, validUntil);
        changeAuditService.record(actor, TARGET_TYPE_BINDING, bindingId, "CREATE",
                null, 0L, requestId, traceId);
        return bindingId;
    }

    /**
     * 创建立即生效的新 Binding：生效时间由服务端时钟决定，调用方不再传入
     * {@code Instant.now()}。
     */
    public UUID createBinding(
            AuthenticatedPrincipal actor, SubjectRef subject, UUID roleId, Scope scope,
            @Nullable Instant validUntil, @Nullable String requestId, @Nullable String traceId) {
        return createBinding(actor, subject, roleId, scope,
                Instant.now(clock), validUntil, requestId, traceId);
    }

    /**
     * 撤销 Binding（逻辑撤销，不是物理删除）。
     *
     * @throws BusinessException 当 Binding 属于操作者自身、不存在，或已被撤销时。
     */
    public void revokeBinding(
            AuthenticatedPrincipal actor, UUID bindingId, @Nullable String reason,
            @Nullable String requestId, @Nullable String traceId) {
        administrationGuard.requireManager(actor);
        SubjectBindingRepository.PersistedBinding binding = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        administrationGuard.requireBindingRevocation(actor, binding);
        bindingRepository.revoke(bindingId, Instant.now(clock), reason)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        changeAuditService.record(actor, TARGET_TYPE_BINDING, bindingId, "REVOKE",
                null, null, requestId, traceId);
    }

    /**
     * 返回主体当前时刻的全部 live Binding。供管理查询与决策引擎的
     * {@code BindingResolver} 使用。
     */
    @Transactional(readOnly = true)
    public List<SubjectBindingRepository.PersistedBinding> liveBindings(SubjectRef subject) {
        return bindingRepository.findLiveBindings(subject, Instant.now(clock));
    }
}
