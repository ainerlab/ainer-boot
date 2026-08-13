package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationTemplate;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository {
    UUID save(NotificationTemplate template);
    Optional<NotificationTemplate> findActiveByCode(String code);
}
