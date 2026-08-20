package dev.ainer.module.notification.notification.infrastructure;

import dev.ainer.module.notification.notification.application.NotificationAuditRepository;
import dev.ainer.module.notification.notification.domain.NotificationAudit;
import org.springframework.stereotype.Repository;

/** {@code ainer_notification_audit} 的 MyBatis 适配器。 */
@Repository
public class MybatisNotificationAuditRepository implements NotificationAuditRepository {

    private final NotificationAuditMapper mapper;

    public MybatisNotificationAuditRepository(NotificationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(NotificationAudit audit) {
        NotificationAuditRow row = new NotificationAuditRow();
        row.setId(audit.id());
        row.setOperation(audit.operation());
        row.setTemplateId(audit.templateId());
        row.setActorIssuer(audit.actorIssuer());
        row.setActorType(audit.actorType());
        row.setActorId(audit.actorId());
        row.setRequestId(audit.requestId());
        row.setOccurredAt(audit.occurredAt());
        mapper.insert(row);
    }
}
