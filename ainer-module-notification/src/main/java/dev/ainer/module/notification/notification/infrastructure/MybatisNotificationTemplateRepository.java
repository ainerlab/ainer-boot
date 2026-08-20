package dev.ainer.module.notification.notification.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ainer.module.notification.notification.application.NotificationTemplateRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link NotificationTemplateRepository} 的 MyBatis 适配器，对应表 {@code ainer_notification_template}。
 * variablesSchema 在 JSONB 与 Map 之间双向转换；更新与状态迁移基于乐观锁版本条件。
 */
@Repository
public class MybatisNotificationTemplateRepository implements NotificationTemplateRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final NotificationTemplateMapper mapper;

    public MybatisNotificationTemplateRepository(NotificationTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID save(NotificationTemplate template) {
        NotificationTemplateRow row = new NotificationTemplateRow();
        row.setId(template.id());
        row.setCode(template.code());
        row.setChannel(template.channel().name());
        row.setTitleTemplate(template.titleTemplate());
        row.setBodyTemplate(template.bodyTemplate());
        row.setVariablesSchema(toJson(template.variablesSchema()));
        row.setStatus(template.status().name());
        row.setVersion(template.version());
        return mapper.insertReturningId(row, Instant.now());
    }

    @Override
    public Optional<NotificationTemplate> findActiveByCode(String code) {
        return Optional.ofNullable(mapper.selectActiveByCode(code))
                .map(MybatisNotificationTemplateRepository::toDomain);
    }

    @Override
    public Optional<NotificationTemplate> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
                .map(MybatisNotificationTemplateRepository::toDomain);
    }

    @Override
    public boolean update(UUID id, @Nullable String titleTemplate, @Nullable String bodyTemplate,
            @Nullable Map<String, Object> variablesSchema, long expectedVersion, long newVersion) {
        return mapper.update(id, titleTemplate, bodyTemplate,
                variablesSchema == null ? null : toJson(variablesSchema),
                expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public boolean updateStatus(UUID id, String status, long expectedVersion, long newVersion) {
        return mapper.updateStatus(id, status, expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public dev.ainer.module.notification.notification.application.NotificationPageSlice<NotificationTemplate> findPage(
            @Nullable String status, long offset, int size) {
        java.util.List<NotificationTemplate> items = mapper.selectPage(status, offset, size).stream()
                .map(MybatisNotificationTemplateRepository::toDomain).toList();
        return new dev.ainer.module.notification.notification.application.NotificationPageSlice<>(
                items, mapper.countPage(status));
    }

    private static NotificationTemplate toDomain(NotificationTemplateRow row) {
        return new NotificationTemplate(
                row.getId(), row.getCode(), NotificationChannel.valueOf(row.getChannel()),
                row.getTitleTemplate(), row.getBodyTemplate(),
                fromJson(row.getVariablesSchema()),
                NotificationTemplate.NotificationTemplateStatus.valueOf(row.getStatus()),
                row.getVersion());
    }

    private static String toJson(Map<String, Object> map) {
        try { return JSON.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) { return Map.of(); }
        try { return JSON.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }
}
