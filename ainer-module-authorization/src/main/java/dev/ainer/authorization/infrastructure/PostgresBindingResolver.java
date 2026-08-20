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
 * PostgreSQL 支撑的 {@link BindingResolver}（ADR-0030 S1）。替换 S0 的内存夹具：
 * 决策引擎现在从持久化存储解析 live Binding。撤销立即生效——没有 ALLOW 缓存，
 * 仍然有效的 JWT 无法恢复已撤销的数据库授权。
 *
 * <p>GLOBAL Binding 只对 SERVICE 主体有效；S0 引擎独立强制该不变量，因此本解析器
 * 返回全部 live Binding 而不按 scope 种类过滤。
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
