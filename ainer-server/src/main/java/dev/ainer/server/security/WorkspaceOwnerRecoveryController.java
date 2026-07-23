package dev.ainer.server.security;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryService;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/workspace-owner-recovery/tenants/{tenantId}")
@ConditionalOnProperty(
        prefix = "ainer.workspace.owner-recovery",
        name = "enabled",
        havingValue = "true")
public class WorkspaceOwnerRecoveryController {

    private static final String REQUEST = "SCOPE_workspace.owner-recovery.request";
    private static final String REQUEST_ALL = "SCOPE_workspace.owner-recovery.request.all";
    private static final String APPROVE = "SCOPE_workspace.owner-recovery.approve";
    private static final String APPROVE_ALL = "SCOPE_workspace.owner-recovery.approve.all";

    private final WorkspaceOwnerRecoveryService recoveryService;
    private final WorkspaceOwnerRecoverySettings settings;
    private final Counter requestedCounter;
    private final Counter executedCounter;

    public WorkspaceOwnerRecoveryController(
            WorkspaceOwnerRecoveryService recoveryService,
            WorkspaceOwnerRecoverySettings settings,
            MeterRegistry meterRegistry) {
        this.recoveryService = recoveryService;
        this.settings = settings;
        this.requestedCounter = meterRegistry.counter("ainer.workspace.owner.recovery.requested");
        this.executedCounter = meterRegistry.counter("ainer.workspace.owner.recovery.executed");
    }

    @PostMapping("/requests")
    public ApiResponse<WorkspaceOwnerRecoveryResponse> requestRecovery(
            @PathVariable String tenantId,
            @Valid @RequestBody WorkspaceOwnerRecoveryRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(
                authentication, tenantId, REQUEST, REQUEST_ALL);
        WorkspaceOwnerRecoveryResponse response = WorkspaceOwnerRecoveryResponse.from(
                recoveryService.requestRecovery(
                        service.serviceId(), new TenantId(tenantId), body.workspaceId(),
                        new SubjectId(body.newOwnerSubjectId()), body.incidentReference(),
                        settings.approvalTtl()));
        requestedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/requests/{requestId}/approvals")
    public ApiResponse<WorkspaceOwnerRecoveryResponse> approve(
            @PathVariable String tenantId,
            @PathVariable UUID requestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(
                authentication, tenantId, APPROVE, APPROVE_ALL);
        WorkspaceOwnerRecoveryResponse response = WorkspaceOwnerRecoveryResponse.from(
                recoveryService.approveAndExecute(
                        service.serviceId(), new TenantId(tenantId), requestId));
        executedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireTenantAccess(
            Authentication authentication,
            String tenantId,
            String tenantAuthority,
            String allAuthority) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        if (service.hasAuthority(allAuthority)) {
            return service;
        }
        service.requireAuthority(tenantAuthority);
        if (!tenantId.equals(service.requireTenantId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return service;
    }
}
