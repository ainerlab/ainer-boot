package dev.ainer.module.notification.notification.api;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.notification.notification.application.NotificationApplicationService;
import dev.ainer.module.notification.notification.application.NotificationErrorCode;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 通知管理 API（ADR-0040）。scope：{@code notification.read}（模板/记录）、
 * {@code notification.manage}（模板生命周期）、{@code notification.submit}（直接发送）。
 * 记录列表省略已渲染的 title/body——收件人与内容属于 PII。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationManagementController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final NotificationApplicationService service;

    public NotificationManagementController(
            AuthenticatedPrincipalResolver principalResolver,
            NotificationApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    // ---- 模板 ----

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<NotificationApiDtos.NotificationTemplateResponse>> createTemplate(
            @RequestBody NotificationApiDtos.CreateTemplateRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UUID id = service.createTemplate(principal, RequestIds.currentOrCreate(request),
                body.code(), parseChannel(body.channel()), body.titleTemplate(), body.bodyTemplate(),
                body.variablesSchema() == null ? java.util.Map.of() : body.variablesSchema());
        NotificationApiDtos.NotificationTemplateResponse response = service
                .getTemplate(principal, id)
                .map(NotificationApiDtos.NotificationTemplateResponse::from)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/templates")
    public ApiResponse<NotificationApiDtos.NotificationTemplatePageResponse> pageTemplates(
            @RequestParam(value = "status", required = false) @Nullable String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                NotificationApiDtos.NotificationTemplatePageResponse.from(
                        service.pageTemplates(principal, status, page, size), page, size),
                RequestIds.currentOrCreate(request));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<NotificationApiDtos.NotificationTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @RequestBody NotificationApiDtos.UpdateTemplateRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                NotificationApiDtos.NotificationTemplateResponse.from(
                        service.updateTemplate(principal, RequestIds.currentOrCreate(request), id,
                                body.titleTemplate(), body.bodyTemplate(), body.variablesSchema(),
                                body.expectedVersion())),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/templates/{id}/status-changes")
    public ApiResponse<NotificationApiDtos.NotificationTemplateResponse> changeTemplateStatus(
            @PathVariable UUID id,
            @RequestBody NotificationApiDtos.StatusChangeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                NotificationApiDtos.NotificationTemplateResponse.from(
                        service.changeTemplateStatus(principal, RequestIds.currentOrCreate(request),
                                id, parseTemplateStatus(body.status()), body.expectedVersion())),
                RequestIds.currentOrCreate(request));
    }

    // ---- 直接提交 ----

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<java.util.Map<String, UUID>>> submitDirect(
            @RequestBody NotificationApiDtos.SubmitDirectRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UUID id = service.submit(principal, RequestIds.currentOrCreate(request),
                new NotificationIntent.DirectIntent(
                        parseChannel(body.channel()), body.recipient(), body.title(), body.body(),
                        body.payload()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(java.util.Map.of("id", id),
                        RequestIds.currentOrCreate(request)));
    }

    // ---- 投递记录 ----

    @GetMapping("/records")
    public ApiResponse<NotificationApiDtos.NotificationRecordPageResponse> pageRecords(
            @RequestParam(value = "status", required = false) @Nullable String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                NotificationApiDtos.NotificationRecordPageResponse.from(
                        service.pageRecords(principal, status, page, size), page, size),
                RequestIds.currentOrCreate(request));
    }

    private static NotificationChannel parseChannel(String channel) {
        try {
            return NotificationChannel.valueOf(channel.strip().toUpperCase());
        } catch (RuntimeException exception) {
            throw new BusinessException(NotificationErrorCode.INVALID_REQUEST);
        }
    }

    private static NotificationTemplate.NotificationTemplateStatus parseTemplateStatus(String status) {
        try {
            return NotificationTemplate.NotificationTemplateStatus.valueOf(
                    status.strip().toUpperCase());
        } catch (RuntimeException exception) {
            throw new BusinessException(NotificationErrorCode.INVALID_REQUEST);
        }
    }
}
