package dev.ainer.authorization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 决策审计历史分页的稳定游标。
 *
 * <p>游标是 {@code (evaluated_at, decision_id)} 全序键，与热表、归档表的主键/索引同形。
 * 归档只是把同一行从热表搬到归档表，不改变它的键，因此用该游标翻页不会出现空洞或重复：
 * 已翻过的行键严格大于游标，不会再被任何一页选中。
 *
 * @param evaluatedAt  决策求值时间
 * @param decisionId   决策 id（UUIDv7，同 {@code evaluated_at} 下唯一）
 */
public record AuthorizationDecisionAuditCursor(Instant evaluatedAt, UUID decisionId) {

    public AuthorizationDecisionAuditCursor {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(decisionId, "decisionId");
    }
}
