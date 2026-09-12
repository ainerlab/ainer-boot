package dev.ainer.authorization.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 决策审计的生命周期持久化端口（ADR-0037 §12.4 保留策略）。
 *
 * <p>与 {@link AuthorizationDecisionAuditRepository} 分开是有意的：后者是决策写入路径的端口，
 * 只有 {@code insert}，请求链路即使被攻破也无法删除审计；本端口持有归档与删除能力，只由
 * {@link AuthorizationDecisionAuditLifecycleService} 在事务内使用。
 *
 * <p>{@link #archiveBefore} 必须在单个语句/事务内完成「先归档后删除」，且删除的前提是同 ID
 * 归档行确实存在——不允许出现删除成功但归档缺失的窗口。
 */
public interface AuthorizationDecisionAuditLifecycleRepository {

    /**
     * 把 {@code evaluated_at < cutoff} 的热行批量搬进归档表，返回实际删除的热行数。
     *
     * <p>实现必须使用 {@code FOR UPDATE SKIP LOCKED} 选择候选行：多实例并发执行同一区间时，
     * 一个实例锁住的行由另一个实例跳过，既不重复归档也不阻塞，更不会丢行。
     *
     * @param cutoff     保留期截止时刻（严格小于该时刻的行才会被归档）
     * @param archivedAt 归档时间戳，写入归档表的 {@code archived_at}
     * @param batchSize  单批上限，必须为正
     * @return 本批实际从热表删除的行数
     */
    int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize);

    /**
     * 按 {@code (evaluated_at, decision_id)} 倒序读取某个 workspace 的热+冷并集，最多
     * {@code limit} 条。分页游标的推进与「是否还有下一页」由
     * {@link AuthorizationDecisionAuditLifecycleService} 负责。
     *
     * @param workspaceId workspace 归属键
     * @param cursor      上一页游标；{@code null} 表示从最新一条开始
     * @param limit       本页上限
     */
    List<AuthorizationDecisionAudit> findPage(
            UUID workspaceId, @Nullable AuthorizationDecisionAuditCursor cursor, int limit);

    /**
     * 读取热表/归档表规模与最旧热行时间，用于指标与告警。
     */
    AuthorizationDecisionAuditOperationalStatus operationalStatus();
}
