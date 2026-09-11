package dev.ainer.authorization.infrastructure;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * {@code ainer_authorization_decision_audit} / {@code ..._archive} 保留状态的聚合行。
 */
public class AuthorizationDecisionAuditOperationalStatusRow {

    private long hot;
    private long archived;
    private @Nullable Instant oldestHotAt;

    public long getHot() { return hot; }
    public void setHot(long hot) { this.hot = hot; }

    public long getArchived() { return archived; }
    public void setArchived(long archived) { this.archived = archived; }

    public @Nullable Instant getOldestHotAt() { return oldestHotAt; }
    public void setOldestHotAt(Instant oldestHotAt) { this.oldestHotAt = oldestHotAt; }
}
