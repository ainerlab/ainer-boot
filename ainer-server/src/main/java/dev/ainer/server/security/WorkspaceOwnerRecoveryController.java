package dev.ainer.server.security;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryService;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
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
@RequestMapping("/internal/workspace-owner-recovery/workspaces/{workspaceId}")
@ConditionalOnProperty(
        prefix = "ainer.workspace.owner-recovery",
        name = "enabled",
        havingValue = "true")
public class WorkspaceOwnerRecoveryController {

    private static final String REQUEST_ALL = "SCOPE_workspace.owner-recovery.request.all";
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
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceOwnerRecoveryRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireWorkspaceAccess(authentication, REQUEST_ALL);
        WorkspaceOwnerRecoveryResponse response = WorkspaceOwnerRecoveryResponse.from(
                recoveryService.requestRecovery(
                        service.serviceId(), workspaceId,
                        new SubjectId(body.newOwnerSubjectId()), body.incidentReference(),
                        settings.approvalTtl()));
        requestedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/requests/{requestId}/approvals")
    public ApiResponse<WorkspaceOwnerRecoveryResponse> approve(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireWorkspaceAccess(authentication, APPROVE_ALL);
        WorkspaceOwnerRecoveryResponse response = WorkspaceOwnerRecoveryResponse.from(
                recoveryService.approveAndExecute(
                        service.serviceId(), requestId));
        executedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireWorkspaceAccess(
            Authentication authentication,
            String allAuthority) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(allAuthority);
        return service;
    }
}
