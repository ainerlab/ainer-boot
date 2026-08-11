package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.core.error.BusinessException;
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
 */
@Service
@Transactional
public class SubjectBindingApplicationService {

    private final SubjectBindingRepository bindingRepository;
    private final RoleRepository roleRepository;
    private final Clock clock;

    public SubjectBindingApplicationService(
            SubjectBindingRepository bindingRepository, RoleRepository roleRepository, Clock clock) {
        this.bindingRepository = bindingRepository;
        this.roleRepository = roleRepository;
        this.clock = clock;
    }

    /**
     * Create a new binding.
     *
     * @throws BusinessException if the role does not exist.
     */
    public UUID createBinding(SubjectRef subject, UUID roleId, Scope scope, Instant validFrom, Instant validUntil) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
        return bindingRepository.save(subject, roleId, scope, validFrom, validUntil);
    }

    /**
     * Revoke a binding (logical, not physical delete).
     *
     * @throws BusinessException if the binding is not found or already revoked.
     */
    public void revokeBinding(UUID bindingId, String reason) {
        bindingRepository.revoke(bindingId, Instant.now(clock), reason)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
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
