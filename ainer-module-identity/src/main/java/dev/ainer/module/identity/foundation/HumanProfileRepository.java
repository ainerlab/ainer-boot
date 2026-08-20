package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link HumanProfile} 的持久化端口（ADR-0033 Greenfield §4，执行计划 缺口 A）。
 *
 * <p>0:1 聚合：按账号读取；upsert 替换或插入该账号唯一的档案行。端口只暴露类型化
 * 聚合；任何操作方都无法在无账号的情况下创建档案。
 */
public interface HumanProfileRepository {

    Optional<HumanProfile> findByAccountId(UUID accountId);

    void upsert(HumanProfile profile);

    void update(HumanProfile profile);
}