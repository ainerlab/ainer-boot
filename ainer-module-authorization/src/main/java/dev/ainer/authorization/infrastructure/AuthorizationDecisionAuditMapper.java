package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code ainer_authorization_decision_audit} 与归档表
 * {@code ainer_authorization_decision_audit_archive} 的 MyBatis mapper（ADR-0030 §12.4、
 * ADR-0037 §12.4 保留策略）。
 *
 * <p>写入路径只有 {@link #insert}（append-only）；{@link #archiveBefore} 是唯一的删除入口，
 * 且删除前提是同 {@code decision_id} 的归档行确实存在。
 */
@Mapper
public interface AuthorizationDecisionAuditMapper {

    /**
     * 插入一条决策审计行。{@code decision_id} 是应用侧提供的 decisionId（UUIDv7），
     * 即主键。
     */
    int insert(@Param("row") AuthorizationDecisionAuditRow row);

    /**
     * 单语句原子归档：{@code FOR UPDATE SKIP LOCKED} 选择候选行 → {@code INSERT ... ON CONFLICT
     * DO NOTHING} 写入归档表 → 仅当归档行确实存在（本语句刚插入，或并发事务已插入）时删除热行。
     *
     * <p>返回值为实际删除的热行数。并发实例之间通过行锁互斥：被别的实例锁住的候选行会被
     * {@code SKIP LOCKED} 跳过而不是阻塞等待，因此同一区间不会被重复归档，也不会丢行；
     * 跳过的行在后续批次/周期里被重新选中。
     */
    int archiveBefore(
            @Param("cutoff") Instant cutoff,
            @Param("archivedAt") Instant archivedAt,
            @Param("limit") int limit);

    /**
     * 按 {@code (evaluated_at, decision_id)} 倒序读取热表与归档表的并集一页。
     * {@code afterEvaluatedAt}/{@code afterDecisionId} 为稳定游标，二者同为 null 时从最新开始。
     */
    List<AuthorizationDecisionAuditRow> selectPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("afterEvaluatedAt") Instant afterEvaluatedAt,
            @Param("afterDecisionId") UUID afterDecisionId,
            @Param("limit") int limit);

    /** 热表行数、归档表行数与最旧热行时间。 */
    AuthorizationDecisionAuditOperationalStatusRow selectOperationalStatus();
}
