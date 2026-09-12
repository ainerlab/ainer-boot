package dev.ainer.authorization.application;

import java.util.List;
import java.util.Objects;

/**
 * 决策审计历史的一页（热表与归档表的并集，按 {@code (evaluated_at, decision_id)} 倒序）。
 *
 * @param items      本页记录，最多 {@code limit} 条
 * @param nextCursor 下一页游标；{@code null} 表示已无更多记录
 * @param hasMore    是否还有下一页
 */
public record AuthorizationDecisionAuditPage(
        List<AuthorizationDecisionAudit> items,
        AuthorizationDecisionAuditCursor nextCursor,
        boolean hasMore) {

    public AuthorizationDecisionAuditPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (hasMore && nextCursor == null) {
            throw new IllegalArgumentException("nextCursor is required when hasMore is true");
        }
    }
}
