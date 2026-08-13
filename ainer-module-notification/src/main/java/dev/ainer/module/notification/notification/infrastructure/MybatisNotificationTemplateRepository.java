package dev.ainer.module.notification.notification.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ainer.module.notification.notification.application.NotificationTemplateRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
