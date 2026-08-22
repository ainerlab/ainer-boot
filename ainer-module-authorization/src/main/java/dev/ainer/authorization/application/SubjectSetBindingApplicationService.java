package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectSetRef;
import dev.ainer.authorization.policy.SubjectSetMembershipRegistry;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 主体集合 Binding 的应用用例（ADR-0042 O2）。集合 Binding 与直接 Binding 共享
 * Role/Scope/时间/撤销语义；授权只有通过决策时的成员关系才到达请求主体。创建时做防
 * 间接提权防护（禁止 GLOBAL、禁止 system-only/HIGH 风险权限、set 与 scope 的
 * Workspace 一致性、集合族必须已注册、禁止自成员）。
 */
@Service
@Transactional
public class SubjectSetBindingApplicationService {

    static final String TARGET_TYPE_SET_BINDING = "SET_BINDING";

    private final SubjectSetBindingRepository bindingRepository;
    private final RoleRepository roleRepository;
    private final GrantAdministrationGuard administrationGuard;
    private final SubjectSetMembershipRegistry membershipRegistry;
    private final AuthorizationChangeAuditService changeAuditService;
    private final Clock clock;

    public SubjectSetBindingApplicationService(
            SubjectSetBindingRepository bindingRepository,
            RoleRepository roleRepository,
            GrantAdministrationGuard administrationGuard,
            SubjectSetMembershipRegistry membershipRegistry,
            AuthorizationChangeAuditService changeAuditService,
            Clock clock) {
        this.bindingRepository = bindingRepository;
        this.roleRepository = roleRepository;
        this.administrationGuard = administrationGuard;
        this.membershipRegistry = membershipRegistry;
        this.changeAuditService = changeAuditService;
        this.clock = clock;
    }

    public UUID createSetBinding(
            AuthenticatedPrincipal actor, SubjectSetRef set, UUID roleId, Scope scope,
            Instant validFrom, @Nullable Instant validUntil,
            @Nullable String requestId, @Nullable String traceId) {
        RoleRepository.RoleRecord role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
        administrationGuard.requireSetBindingCreation(actor, set, role, scope, membershipRegistry);
        UUID bindingId = bindingRepository.save(set, roleId, scope, validFrom, validUntil);
        changeAuditService.record(actor, TARGET_TYPE_SET_BINDING, bindingId, "CREATE",
                null, 0L, requestId, traceId);
        return bindingId;
    }

    /** 创建立即生效的集合 Binding：生效时间由服务端时钟决定。 */
    public UUID createSetBinding(
            AuthenticatedPrincipal actor, SubjectSetRef set, UUID roleId, Scope scope,
            @Nullable Instant validUntil, @Nullable String requestId, @Nullable String traceId) {
        return createSetBinding(actor, set, roleId, scope,
                Instant.now(clock), validUntil, requestId, traceId);
    }

    public void revokeSetBinding(
            AuthenticatedPrincipal actor, UUID bindingId, @Nullable String reason,
            @Nullable String requestId, @Nullable String traceId) {
        administrationGuard.requireManager(actor);
        SubjectSetBindingRepository.PersistedSetBinding binding = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        if (binding.status() != dev.ainer.authorization.domain.BindingStatus.ACTIVE) {
            throw new BusinessException(AuthorizationErrorCode.SET_BINDING_ALREADY_REVOKED);
        }
        bindingRepository.revoke(bindingId, Instant.now(clock), reason)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        changeAuditService.record(actor, TARGET_TYPE_SET_BINDING, bindingId, "REVOKE",
                null, null, requestId, traceId);
    }

    @Transactional(readOnly = true)
    public Optional<SubjectSetBindingRepository.PersistedSetBinding> findById(UUID id) {
        return bindingRepository.findById(id);
    }
}
