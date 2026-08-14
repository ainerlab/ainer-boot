package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationAudit;

/**
 * Persistence port for append-only {@link NotificationAudit} rows (ADR-0040).
 */
public interface NotificationAuditRepository {

    void insert(NotificationAudit audit);
}
