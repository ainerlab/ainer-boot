package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.RoleRepository;
import dev.ainer.authorization.application.SubjectBindingRepository;
import dev.ainer.authorization.application.SubjectSetBindingRepository;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.Role;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetBinding;
import dev.ainer.authorization.policy.BindingResolver;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PostgreSQL-backed {@link BindingResolver} (ADR-0030 S1). Replaces the S0 in-memory fixture:
 * the decision engine now resolves live bindings from the persisted store. Revocation is
 * reflected immediately — there is no ALLOW cache, and a still-valid JWT cannot restore a
 * revoked database grant.
 *
 * <p>GLOBAL bindings are only valid for SERVICE subjects; the S0 engine enforces this invariant
 * independently, so this resolver returns all live bindings without filtering by scope kind.
 */
@Component
public class PostgresBindingResolver implements BindingResolver {

    private final SubjectBindingRepository bindingRepository;
    private final SubjectSetBindingRepository setBindingRepository;
    private final RoleRepository roleRepository;
    private final Clock clock;

    public PostgresBindingResolver(
            SubjectBindingRepository bindingRepository,
            SubjectSetBindingRepository setBindingRepository,
            RoleRepository roleRepository,
            Clock clock) {
        this.bindingRepository = bindingRepository;
        this.setBindingRepository = setBindingRepository;
        this.roleRepository = roleRepository;
        this.clock = clock;
    }

    @Override
    public java.util.List<SubjectSetBinding> liveSetBindings(ResourceRef resource, java.time.Instant at) {
        java.util.List<SubjectSetBindingRepository.PersistedSetBinding> persisted =
                setBindingRepository.findLiveSetBindings(resource, at);
        java.util.List<SubjectSetBinding> result = new java.util.ArrayList<>(persisted.size());
        for (SubjectSetBindingRepository.PersistedSetBinding pb : persisted) {
            RoleRepository.RoleRecord roleRecord = roleRepository.findById(pb.roleId()).orElse(null);
            if (roleRecord == null) {
                continue;
            }
            result.add(new SubjectSetBinding(pb.id(), pb.set(), roleRecord.role(), pb.scope(),
                    BindingStatus.ACTIVE, pb.validFrom(), pb.validUntil(), pb.version()));
        }
        return result;
    }

    @Override
    public Set<SubjectBinding> liveBindings(SubjectRef subject) {
        List<SubjectBindingRepository.PersistedBinding> persisted =
                bindingRepository.findLiveBindings(subject, java.time.Instant.now(clock));
        Set<SubjectBinding> result = new HashSet<>(persisted.size());
        for (SubjectBindingRepository.PersistedBinding pb : persisted) {
            RoleRepository.RoleRecord roleRecord = roleRepository.findById(pb.roleId()).orElse(null);
            if (roleRecord == null) {
                continue;
            }
            // 直接复用 RoleRecord 的 domain Role（含 code/name/permissions），无需用 roleCode 重新构造。
            Role role = roleRecord.role();
            result.add(new SubjectBinding(
                    pb.subjectRef(),
                    role,
                    pb.scope(),
                    BindingStatus.ACTIVE,
                    pb.validFrom(),
                    pb.validUntil(),
                    pb.version()));
        }
        return result;
    }
}
