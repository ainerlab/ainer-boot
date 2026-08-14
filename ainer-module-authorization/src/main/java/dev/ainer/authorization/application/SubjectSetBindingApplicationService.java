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
 * Application use cases for subject-set bindings (ADR-0042 O2). Set bindings share Role/Scope/
 * time/revocation semantics with direct bindings; the grant reaches a requesting subject only
 * through decision-time membership. Creation is guarded against indirect privilege escalation
 * (no GLOBAL, no system-only/HIGH-risk permissions, set↔scope workspace consistency, registered
 * set family, no self-membership).
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
