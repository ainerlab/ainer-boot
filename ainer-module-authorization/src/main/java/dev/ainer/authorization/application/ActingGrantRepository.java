package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.ActingGrant;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link ActingGrant} 聚合的持久化端口（ADR-0043 A1）。 */
public interface ActingGrantRepository {

    /** 持久化授权，附带数据库身份的领域值。 */
    record PersistedGrant(
            UUID id,
            SubjectRef principal,
            UUID agentId,
            String agentVersion,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version,
            Set<PermissionCode> permissions) {
    }

    UUID save(SubjectRef principal, UUID agentId, String agentVersion, Set<PermissionCode> permissions,
            Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedGrant> findById(UUID id);

    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /** principal 匹配且有效时间窗口覆盖 {@code at} 的 live 授权。 */
    List<PersistedGrant> findLiveGrants(SubjectRef principal, Instant at);
}
