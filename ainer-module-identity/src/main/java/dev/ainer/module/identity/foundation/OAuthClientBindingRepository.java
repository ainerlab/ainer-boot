package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link OAuthClientBinding} 的持久化端口（ADR-0033 Greenfield §2.6）。
 *
 * <p>绑定把可轮换的 OAuth {@code client_id} 连接到稳定的 {@link ServicePrincipal}。
 * {@code (client_id) WHERE status = 'ACTIVE'} 上的部分唯一索引保证同一凭证任一时刻至多
 * 一个活跃绑定；退役绑定保留供审计与历史 introspection。
 */
public interface OAuthClientBindingRepository {

    void save(OAuthClientBinding binding);

    Optional<OAuthClientBinding> findActiveByClientId(String clientId);

    Optional<OAuthClientBinding> findByPrincipalId(UUID principalId);

    /** 绑定行的下一个 PostgreSQL UUIDv7 主键。 */
    UUID nextUuidV7();
}
