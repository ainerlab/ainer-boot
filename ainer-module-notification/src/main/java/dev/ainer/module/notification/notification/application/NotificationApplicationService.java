package dev.ainer.module.notification.notification.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.notification.notification.domain.NotificationAudit;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 提交通知意图与管理模板的应用服务（ADR-0040 管理面硬化）。模板管理要求
 * {@code notification.manage}，读取要求 {@code notification.read}，提交要求
 * {@code notification.submit}；模板变更写入同事务的 {@link NotificationAudit} 行。
 * 投递事实保存在 {@code ainer_notification_record}，分页提供给运维。
 */
@Service
@Transactional
public class NotificationApplicationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRecordRepository recordRepository;
    private final NotificationAuditRepository auditRepository;
    private final Clock clock;

    public NotificationApplicationService(
            NotificationTemplateRepository templateRepository,
            NotificationRecordRepository recordRepository,
            NotificationAuditRepository auditRepository,
            Clock clock) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    // ---- 提交 ----

    /** 经管理面提交：要求 {@code notification.submit}。 */
    public UUID submit(AuthenticatedPrincipal principal, @Nullable String requestId,
            NotificationIntent intent) {
        requireScope(principal, NotificationAuthorities.SUBMIT);
        return submitInternal(intent);
    }

    /** 内部提交路径（产品代码，不涉及 HTTP 主体）。 */
    public UUID submit(NotificationIntent intent) {
        return submitInternal(intent);
    }

    private UUID submitInternal(NotificationIntent intent) {
        Objects.requireNonNull(intent, "intent");
        NotificationRecord record = switch (intent) {
            case NotificationIntent.TemplateIntent t -> buildFromTemplate(t);
            case NotificationIntent.DirectIntent d -> buildFromDirect(d);
        };
        return recordRepository.save(record);
    }

    // ---- 模板管理 ----

    public UUID createTemplate(
            AuthenticatedPrincipal principal, @Nullable String requestId, String code,
            NotificationChannel channel, String titleTemplate, String bodyTemplate,
            Map<String, Object> variablesSchema) {
        requireScope(principal, NotificationAuthorities.MANAGE);
        templateRepository.findActiveByCode(code).ifPresent(t -> {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_ALREADY_EXISTS);
        });
        UUID id = dev.ainer.core.uuid.Uuidv7.generate();
        NotificationTemplate template = new NotificationTemplate(
                id, code, channel, titleTemplate, bodyTemplate,
                variablesSchema, NotificationTemplate.NotificationTemplateStatus.ACTIVE, 0);
        UUID saved = templateRepository.save(template);
        audit(principal, requestId, NotificationAudit.OPERATION_TEMPLATE_CREATED, saved);
        return saved;
    }

    public NotificationTemplate updateTemplate(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            @Nullable String titleTemplate, @Nullable String bodyTemplate,
            @Nullable Map<String, Object> variablesSchema, long expectedVersion) {
        requireScope(principal, NotificationAuthorities.MANAGE);
        templateRepository.findById(id).orElseThrow(
                () -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
        if (!templateRepository.update(id, titleTemplate, bodyTemplate, variablesSchema,
                expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(NotificationErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, NotificationAudit.OPERATION_TEMPLATE_UPDATED, id);
        return getTemplateInternal(id);
    }

    public NotificationTemplate changeTemplateStatus(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            NotificationTemplate.NotificationTemplateStatus status, long expectedVersion) {
        requireScope(principal, NotificationAuthorities.MANAGE);
        templateRepository.findById(id).orElseThrow(
                () -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
        if (!templateRepository.updateStatus(id, status.name(),
                expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(NotificationErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, NotificationAudit.OPERATION_TEMPLATE_STATUS_CHANGED, id);
        return getTemplateInternal(id);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> getTemplate(AuthenticatedPrincipal principal, UUID id) {
        requireScope(principal, NotificationAuthorities.READ);
        return templateRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public NotificationPageSlice<NotificationTemplate> pageTemplates(
            AuthenticatedPrincipal principal, @Nullable String status, int page, int size) {
        requireScope(principal, NotificationAuthorities.READ);
        requirePage(page, size);
        return templateRepository.findPage(normalizeStatus(status), (long) (page - 1) * size, size);
    }

    // ---- 投递记录（运维）----

    @Transactional(readOnly = true)
    public NotificationPageSlice<NotificationRecord> pageRecords(
            AuthenticatedPrincipal principal, @Nullable String status, int page, int size) {
        requireScope(principal, NotificationAuthorities.READ);
        requirePage(page, size);
        return recordRepository.findPage(normalizeStatus(status), (long) (page - 1) * size, size);
    }

    // ---- 内部方法 ----

    private NotificationTemplate getTemplateInternal(UUID id) {
        return templateRepository.findById(id).orElseThrow(
                () -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
    }

    private NotificationRecord buildFromTemplate(NotificationIntent.TemplateIntent intent) {
        NotificationTemplate template = templateRepository.findActiveByCode(intent.templateCode())
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
        if (template.channel() != intent.channel()) {
            throw new BusinessException(NotificationErrorCode.CHANNEL_MISMATCH);
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

    private static void requireScope(AuthenticatedPrincipal principal, String scope) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasScope(scope)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(NotificationErrorCode.INVALID_PAGE);
        }
    }

    private static String normalizeStatus(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.strip().toUpperCase();
    }

    private void audit(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            String operation, UUID templateId) {
        auditRepository.insert(new NotificationAudit(
                dev.ainer.core.uuid.Uuidv7.generate(),
                operation,
                templateId,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(),
                requestId,
                clock.instant()));
    }

    /**
     * 简单的 {@code {variable}} 占位替换。更复杂的需求（条件、循环）可插入专用
     * 模板引擎——JSONB schema 负责校验可用变量。
     */
    static String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
