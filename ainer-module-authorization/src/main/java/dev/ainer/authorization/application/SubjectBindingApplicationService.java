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
 * Application use cases for {@link dev.ainer.authorization.domain.SubjectBinding} lifecycle
 * management (ADR-0030 S1). Bindings assign a persisted Role and a structured Scope to a subject
 * over a validity window. Revocation is a logical state transition — a still-valid JWT cannot
 * restore a revoked database grant.
 *
 * <p>Management mutations are audited via {@link AuthorizationChangeAuditService} in the same
 * transaction (ADR-0030 §11.7).
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
     * Create a new binding.
     *
     * @throws BusinessException if the manager/target/scope/Role permissions are not assignable or
     *                           the role does not exist.
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
     * Revoke a binding (logical, not physical delete).
     *
     * @throws BusinessException if the binding belongs to the actor, is not found, or is already revoked.
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
     * Return all live bindings for a subject at the current time. Used by management queries and
     * the decision engine's {@code BindingResolver}.
     */
    @Transactional(readOnly = true)
    public List<SubjectBindingRepository.PersistedBinding> liveBindings(SubjectRef subject) {
        return bindingRepository.findLiveBindings(subject, Instant.now(clock));
    }
}
