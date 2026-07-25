package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Passkey 管理员双人恢复控制面（默认关闭）。见 ADR-0015。两个不同 SERVICE 身份分两阶段，
 * tenant-bound scope 只能操作 Token 中的 tenant，`.all` 可跨 tenant。request 与 approve scope
 * 必须授予不同 Client。
 */
@Validated
@RestController
@RequestMapping("/internal/passkey-recovery/tenants/{tenantId}")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.passkey.recovery",
        name = "enabled",
        havingValue = "true")
public class AinerPasskeyRecoveryController {

    private static final String REQUEST = "SCOPE_passkey.recovery.request";
    private static final String REQUEST_ALL = "SCOPE_passkey.recovery.request.all";
    private static final String APPROVE = "SCOPE_passkey.recovery.approve";
    private static final String APPROVE_ALL = "SCOPE_passkey.recovery.approve.all";

    private final AinerPasskeyAdminRecoveryService recoveryService;
    private final AinerPasskeyRecoveryProperties settings;
    private final Counter requestedCounter;
    private final Counter executedCounter;

    public AinerPasskeyRecoveryController(
            AinerPasskeyAdminRecoveryService recoveryService,
            AinerPasskeyRecoveryProperties settings,
            MeterRegistry meterRegistry) {
        this.recoveryService = recoveryService;
        this.settings = settings;
        this.requestedCounter = meterRegistry.counter("ainer.passkey.recovery.requested");
        this.executedCounter = meterRegistry.counter("ainer.passkey.recovery.executed");
    }

    @PostMapping("/recovery-requests")
    public ApiResponse<RecoveryRequestResponse> requestRecovery(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RecoveryRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(authentication, tenantId, REQUEST, REQUEST_ALL);
        RecoveryRequestResponse response = RecoveryRequestResponse.from(
                recoveryService.requestRecovery(
                        service.serviceId(), tenantId, body.subjectId(),
                        body.incidentReference(), settings.getApprovalTtl()));
        requestedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/recovery-requests/{requestId}/approvals")
    public ApiResponse<RecoveryRequestResponse> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID requestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(authentication, tenantId, APPROVE, APPROVE_ALL);
        RecoveryRequestResponse response = RecoveryRequestResponse.from(
                recoveryService.approveAndExecute(service.serviceId(), tenantId, requestId));
        executedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireTenantAccess(
            Authentication authentication,
            UUID tenantId,
            String tenantAuthority,
            String allAuthority) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        if (service.hasAuthority(allAuthority)) {
            return service;
        }
        service.requireAuthority(tenantAuthority);
        try {
            if (!tenantId.equals(UUID.fromString(service.requireTenantId()))) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return service;
    }

    public record RecoveryRequestBody(@NotNull UUID subjectId, @NotNull String incidentReference) {
    }

    public record RecoveryRequestResponse(
            UUID id, UUID tenantId, UUID subjectId, String requestedBy, String approvedBy,
            String incidentReference, String status) {
        static RecoveryRequestResponse from(AinerPasskeyAdminRecoveryService.RecoveryRequest request) {
            return new RecoveryRequestResponse(
                    request.id(), request.tenantId(), request.subjectId(),
                    request.requestedBy(), request.approvedBy(),
                    request.incidentReference(), request.status());
        }
    }
}
