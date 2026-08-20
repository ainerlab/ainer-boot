package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知模板的持久化端口（ADR-0040）。模板按编码唯一启用；
 * 更新与状态迁移都基于乐观锁版本，供管理面与模板渲染路径使用。
 */
public interface NotificationTemplateRepository {
    UUID save(NotificationTemplate template);
    Optional<NotificationTemplate> findActiveByCode(String code);
    Optional<NotificationTemplate> findById(UUID id);

    /** Partial update with optimistic locking; {@code null} fields keep their stored value. */
    boolean update(UUID id, @Nullable String titleTemplate, @Nullable String bodyTemplate,
            @Nullable Map<String, Object> variablesSchema, long expectedVersion, long newVersion);

    /** Status transition with optimistic locking. */
    boolean updateStatus(UUID id, String status, long expectedVersion, long newVersion);

    /** Page through templates, optionally filtered by status, ordered by code. */
    NotificationPageSlice<NotificationTemplate> findPage(@Nullable String status, long offset, int size);
}
