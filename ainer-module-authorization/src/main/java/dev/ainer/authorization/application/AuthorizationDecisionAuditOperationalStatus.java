package dev.ainer.authorization.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 决策审计保留任务的运行状态快照（指标与告警的数据来源）。
 *
 * @param hot        热表当前行数
 * @param archived   归档表当前行数
 * @param oldestHotAt 最旧热行的 {@code evaluated_at}；热表为空时为 {@code null}
 */
public record AuthorizationDecisionAuditOperationalStatus(
        long hot,
        long archived,
        @Nullable Instant oldestHotAt) {
}
