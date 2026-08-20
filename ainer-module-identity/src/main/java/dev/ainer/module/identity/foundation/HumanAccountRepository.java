package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link HumanAccount} 的持久化端口（ADR-0033 Greenfield §3）。
 *
 * <p>生产实现基于重建后的 Identity 基线（PostgreSQL UUIDv7 主键、按权威限定的查询）。
 * Greenfield 骨架提供进程内实现，使注册 / 登录查找流程在数据库切换前即可运行验证。
 * Identity 不暴露原始存储；调用方只消费类型化聚合。
 */
public interface HumanAccountRepository {

    void save(HumanAccount account);

    Optional<HumanAccount> findByAccountId(UUID accountId);

    /**
     * foundation 聚合的下一个 PostgreSQL UUIDv7 主键。账号仓库负责 ID 生成，
     * 使 {@link IdentityFoundationService} 通过 supplier 保持与持久化无关。
     */
    UUID nextUuidV7();
}
