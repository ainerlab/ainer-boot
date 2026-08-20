package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectSetBinding;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 主体集合 Binding 的持久化端口（ADR-0042 O2），与直接 Binding 端口对应。 */
public interface SubjectSetBindingRepository {

    /** 持久化集合 Binding，附带数据库身份与 Role 引用。 */
    record PersistedSetBinding(
            UUID id,
            SubjectSetRef set,
            UUID roleId,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version) {
    }

    UUID save(SubjectSetRef set, UUID roleId, Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedSetBinding> findById(UUID id);

    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /** scope 覆盖该资源的 live（ACTIVE 且时间有效）集合 Binding；SQL 侧已过滤。 */
    List<PersistedSetBinding> findLiveSetBindings(ResourceRef resource, Instant at);
}
