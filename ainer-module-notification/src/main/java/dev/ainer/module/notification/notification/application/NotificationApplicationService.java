package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for submitting notification intents and managing templates (ADR-0038).
 * Uses JDK 25 switch pattern matching on {@link NotificationIntent} sealed interface to deconstruct
 * the intent and build the {@link NotificationRecord} — no visitor pattern or instanceof chains.
 *
 * <p>Template rendering uses simple {@code {variable}} substitution from the JSONB variables map.
 * The record is persisted as PENDING; the {@link NotificationDeliveryEngine} claims and sends it
 * asynchronously via virtual threads.
 */
@Service
@Transactional
public class NotificationApplicationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRecordRepository recordRepository;
    private final Clock clock;

    public NotificationApplicationService(
            NotificationTemplateRepository templateRepository,
            NotificationRecordRepository recordRepository,
            Clock clock) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.clock = clock;
    }

    /**
     * Submit a notification intent for async delivery. Returns the record ID.
     */
    public UUID submit(NotificationIntent intent) {
        Objects.requireNonNull(intent, "intent");
        // JDK 25 record pattern: deconstruct the sealed intent
        NotificationRecord record = switch (intent) {
            case NotificationIntent.TemplateIntent t -> buildFromTemplate(t);
            case NotificationIntent.DirectIntent d -> buildFromDirect(d);
        };
        return recordRepository.save(record);
    }

    // ---- Template management ----

    public UUID createTemplate(String code, NotificationChannel channel,
                               String titleTemplate, String bodyTemplate,
                               Map<String, Object> variablesSchema) {
        templateRepository.findActiveByCode(code).ifPresent(t -> {
            throw new IllegalArgumentException("Template already exists: " + code);
        });
        UUID id = dev.ainer.core.uuid.Uuidv7.generate();
        Instant now = clock.instant();
        NotificationTemplate template = new NotificationTemplate(
                id, code, channel, titleTemplate, bodyTemplate,
                variablesSchema, NotificationTemplate.NotificationTemplateStatus.ACTIVE, 0);
        return templateRepository.save(template);
    }

    // ---- Internal ----

    private NotificationRecord buildFromTemplate(NotificationIntent.TemplateIntent intent) {
        NotificationTemplate template = templateRepository.findActiveByCode(intent.templateCode())
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + intent.templateCode()));
        if (template.channel() != intent.channel()) {
            throw new IllegalArgumentException(
                    "Template channel mismatch: template=%s, intent=%s".formatted(
                            template.channel(), intent.channel()));
        }
        String title = renderTemplate(template.titleTemplate(), intent.variables());
        String body = renderTemplate(template.bodyTemplate(), intent.variables());
        return newRecord(intent.channel(), intent.recipient(), intent.templateCode(),
                title, body, intent.variables());
    }

    private NotificationRecord buildFromDirect(NotificationIntent.DirectIntent intent) {
        return newRecord(intent.channel(), intent.recipient(), null,
                intent.title(), intent.body(), intent.payload());
    }

    private NotificationRecord newRecord(
            NotificationChannel channel, String recipient, String templateCode,
            String title, String body, Map<String, Object> payload) {
        Instant now = clock.instant();
        return new NotificationRecord(
                dev.ainer.core.uuid.Uuidv7.generate(), templateCode, channel, recipient, title, body, payload,
                NotificationStatus.PENDING, 0, 3, null, null, null, now, now);
    }

    /**
     * Simple {@code {variable}} substitution. For complex needs (conditionals, loops), a dedicated
     * template engine can be plugged in — the JSONB schema validates available variables.
     */
    static String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
