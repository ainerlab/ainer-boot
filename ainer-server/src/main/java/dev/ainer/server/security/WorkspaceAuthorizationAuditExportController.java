package dev.ainer.server.security;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditCursor;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/workspace-authorization-audits/tenants/{tenantId}/exports")
@ConditionalOnProperty(
        prefix = "ainer.workspace.authorization-audit-export",
        name = "enabled",
        havingValue = "true")
public class WorkspaceAuthorizationAuditExportController {

    private static final String EXPORT = "SCOPE_workspace.audit.export";
    private static final String EXPORT_ALL = "SCOPE_workspace.audit.export.all";

    private final WorkspaceAuthorizationAuditLifecycleService lifecycleService;
    private final WorkspaceAuthorizationAuditExportSettings settings;
    private final Counter exportCounter;

    public WorkspaceAuthorizationAuditExportController(
            WorkspaceAuthorizationAuditLifecycleService lifecycleService,
            WorkspaceAuthorizationAuditExportSettings settings,
            MeterRegistry meterRegistry) {
        this.lifecycleService = lifecycleService;
        this.settings = settings;
        this.exportCounter = meterRegistry.counter("ainer.workspace.authorization.audit.exported");
    }

    @GetMapping
    public ApiResponse<WorkspaceAuthorizationAuditExportResponse> export(
            @PathVariable String tenantId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterOccurredAt,
            @RequestParam(required = false) UUID afterId,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireExporter(authentication, tenantId);
        if ((afterOccurredAt == null) != (afterId == null)) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_AUDIT_EXPORT_REQUEST);
        }
        WorkspaceAuthorizationAuditCursor cursor = afterOccurredAt == null
                ? null
                : new WorkspaceAuthorizationAuditCursor(afterOccurredAt, afterId);
        var batch = lifecycleService.export(
                service.serviceId(), new TenantId(tenantId), cursor, limit);
        exportCounter.increment(batch.items().size());
        return ApiResponse.success(
                WorkspaceAuthorizationAuditExportResponse.from(batch),
                RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireExporter(Authentication authentication, String tenantId) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        if (!settings.trustedExporterSubject().equals(service.serviceId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        if (service.hasAuthority(EXPORT_ALL)) {
            return service;
        }
        service.requireAuthority(EXPORT);
        if (!tenantId.equals(service.requireTenantId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return service;
    }
}
