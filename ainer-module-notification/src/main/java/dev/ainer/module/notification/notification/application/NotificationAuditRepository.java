package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationAudit;

/**
 * 只追加 {@link NotificationAudit} 行的持久化端口（ADR-0040）。
 */
public interface NotificationAuditRepository {

    void insert(NotificationAudit audit);
}
