package dev.ainer.authorization.application;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 决策审计的生命周期运维服务（ADR-0037 §12.4 保留策略）：热表归档、热+冷并集历史查询与
 * 运行状态快照。
 *
 * <p>归档在单个事务内完成：{@code FOR UPDATE SKIP LOCKED} 选择过期候选行 → {@code INSERT ...
 * ON CONFLICT DO NOTHING} 写入归档表 → 仅当归档行确实存在时删除热行。因此任何时刻都不会出现
 * 「热行已删但归档缺失」的窗口；归档表本身不自动删除，最终删除与法律保留需要另立策略。
 *
 * <p>历史查询读热表与归档表的并集，游标是 {@code (evaluated_at, decision_id)}：归档只搬运行、
 * 不改变键，所以翻页过程中即使发生归档也不会出现空洞或重复。
 *
 * <p>本服务不暴露 HTTP：它只有运维/后台调用方（保留任务与未来的审计导出）。参数非法属于调用方
 * 编程错误，直接抛出 {@link IllegalArgumentException}，不新增面向客户端的错误码。
 */
@Service
public class AuthorizationDecisionAuditLifecycleService {

    /** 单批上限：防止一次归档事务长时间持有锁并产生巨量 WAL。 */
    static final int MAX_BATCH_SIZE = 5000;

    /** 单页上限：与工作区审计导出的对外上限保持一致。 */
    static final int MAX_PAGE_SIZE = 1000;

    private final AuthorizationDecisionAuditLifecycleRepository repository;
    private final Clock clock;

    public AuthorizationDecisionAuditLifecycleService(
            AuthorizationDecisionAuditLifecycleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 归档一批早于 {@code cutoff} 的热行。
     *
     * @param cutoff    保留期截止时刻；{@code evaluated_at} 严格早于它的行才会被归档
     * @param batchSize 单批上限（1..{@value #MAX_BATCH_SIZE}）
     * @return 本批实际从热表删除（即已搬进归档表）的行数
     */
    @Transactional
    public int archiveBefore(Instant cutoff, int batchSize) {
        Objects.requireNonNull(cutoff, "cutoff");
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Authorization decision audit archive batch size must be between 1 and " + MAX_BATCH_SIZE);
        }
        return repository.archiveBefore(cutoff, clock.instant(), batchSize);
    }

    /**
     * 按 {@code (evaluated_at, decision_id)} 倒序读取某个 workspace 的热+冷并集一页。
     *
     * @param workspaceId workspace 归属键，必填（决策审计不做跨 workspace 的全表浏览）
     * @param cursor      上一页返回的游标；{@code null} 表示从最新一条开始
     * @param limit       本页上限（1..{@value #MAX_PAGE_SIZE}）
     */
    @Transactional(readOnly = true)
    public AuthorizationDecisionAuditPage history(
            UUID workspaceId, @Nullable AuthorizationDecisionAuditCursor cursor, int limit) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Authorization decision audit page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        // 多取一条判断是否还有下一页：避免为分页额外做一次 COUNT。
        List<AuthorizationDecisionAudit> rows = repository.findPage(workspaceId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AuthorizationDecisionAudit> items = hasMore ? List.copyOf(rows.subList(0, limit)) : rows;
        if (!hasMore) {
            return new AuthorizationDecisionAuditPage(items, null, false);
        }
        AuthorizationDecisionAudit last = items.getLast();
        return new AuthorizationDecisionAuditPage(
                items,
                new AuthorizationDecisionAuditCursor(last.evaluatedAt(), last.decisionId()),
                true);
    }

    /**
     * 热表/归档表规模与最旧热行时间（指标与告警快照）。
     */
    @Transactional(readOnly = true)
    public AuthorizationDecisionAuditOperationalStatus status() {
        return repository.operationalStatus();
    }
}
