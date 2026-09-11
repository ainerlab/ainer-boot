package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import dev.ainer.cache.lock.LocalDistributedLockPort;
import dev.ainer.cache.lock.PostgresDistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

/**
 * 分布式锁实现的统一选择点（ADR-0039 §4）。选择结果由 {@code ainer.cache.lock.type} 决定：
 *
 * <table>
 *   <tr><th>lock.type</th><th>选择</th><th>失败/告警</th></tr>
 *   <tr><td>{@code AUTO}（默认）</td>
 *       <td>Redis 锁（若 Redis 缓存后端已装配）→ PG advisory lock（若存在唯一的 DataSource）
 *           → 进程内锁</td>
 *       <td>落到进程内锁时 WARN，明说多实例互斥不成立</td></tr>
 *   <tr><td>{@code POSTGRES}</td><td>{@link PostgresDistributedLockPort}</td>
 *       <td>没有 DataSource bean（或存在多个且无法判定）时<strong>启动失败</strong></td></tr>
 *   <tr><td>{@code LOCAL}</td><td>{@link LocalDistributedLockPort}</td>
 *       <td>WARN，明说多实例互斥不成立</td></tr>
 * </table>
 *
 * <p>Redis 锁的装配留在 {@code AinerRedisCacheAutoConfiguration}（那里才能安全引用 Spring Data
 * Redis 类）；本配置在其之后运行，通过 {@code @ConditionalOnMissingBean} 让位。
 *
 * <p>锁与缓存是两个独立开关：缓存可以关闭（{@code ainer.cache.enabled=false}）而锁照常工作。
 */
@AutoConfiguration(after = AinerRedisCacheAutoConfiguration.class)
@EnableConfigurationProperties(AinerCacheProperties.class)
public class AinerCacheLockAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AinerCacheLockAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(DistributedLockPort.class)
    public DistributedLockPort ainerDistributedLockPort(
            AinerCacheProperties properties, ObjectProvider<DataSource> dataSources) {
        AinerCacheProperties.LockType lockType = properties.lock().type();
        List<DataSource> candidates = dataSources.orderedStream().toList();
        return switch (lockType) {
            case LOCAL -> localLock("ainer.cache.lock.type=LOCAL 显式要求进程内锁");
            case POSTGRES -> postgresLock(requireDataSource(candidates, "ainer.cache.lock.type=POSTGRES"));
            case AUTO -> autoLock(candidates);
        };
    }

    private DistributedLockPort autoLock(List<DataSource> candidates) {
        if (candidates.size() == 1) {
            LOGGER.info("[ainer-cache] ainer.cache.lock.type=AUTO：检测到 DataSource，"
                    + "使用 PostgreSQL 会话级 advisory lock");
            return postgresLock(candidates.get(0));
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException(("""
                    ainer.cache.lock.type=AUTO 检测到 %d 个 DataSource bean，无法判定 advisory lock \
                    使用哪一个。请用 @Primary 指定主数据源，或显式配置 \
                    ainer.cache.lock.type=POSTGRES（配合 @Primary）/ LOCAL。""")
                    .formatted(candidates.size()));
        }
        return localLock("ainer.cache.lock.type=AUTO 且上下文没有 DataSource bean（也没有 Redis 锁）");
    }

    private static DistributedLockPort postgresLock(DataSource dataSource) {
        return new PostgresDistributedLockPort(dataSource);
    }

    private static DistributedLockPort localLock(String reason) {
        LOGGER.warn("""
                [ainer-cache] 分布式锁退化为进程内实现：{}。进程内锁只在单个 JVM 内有效，\
                多实例部署下锁互斥不成立——请配置 ainer.cache.lock.type=POSTGRES（需要 DataSource）\
                或 ainer.cache.type=redis（使用 Redis 锁）。""", reason);
        return new LocalDistributedLockPort();
    }

    private static DataSource requireDataSource(List<DataSource> candidates, String property) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(property + " 需要上下文存在 DataSource bean（PostgreSQL 连接），"
                    + "但当前没有。请配置 spring.datasource.*（或提供 DataSource bean），"
                    + "或改用 ainer.cache.lock.type=AUTO / LOCAL。");
        }
        throw new IllegalStateException(property + " 需要唯一的 DataSource bean，但当前存在 "
                + candidates.size() + " 个。请用 @Primary 指定主数据源。");
    }
}
