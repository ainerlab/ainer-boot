package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link Credential} 材料的持久化端口（ADR-0033 Greenfield §4，执行计划 缺口 A）。
 *
 * <p>以账号 + 类型为键：每个 {@code (accountId, type)} 至多一份 ACTIVE 材料。轮换通过
 * {@link #revokeActive} 再 {@link #save} 新的 ACTIVE 凭证表达，绝不原地修改旧材料。
 * 该端口只暴露类型化聚合。
 */
public interface CredentialRepository {

    void insert(Credential credential);

    Optional<Credential> findActive(UUID accountId, CredentialType type);

    /** 吊销 (account, type) 当前的 ACTIVE 材料（如存在）。返回受影响行数（0 或 1）。 */
    int revokeActive(UUID accountId, CredentialType type, java.time.Instant rotatedAt);

    /** 凭证聚合的下一个 PostgreSQL UUIDv7 主键。 */
    UUID nextUuidV7();
}