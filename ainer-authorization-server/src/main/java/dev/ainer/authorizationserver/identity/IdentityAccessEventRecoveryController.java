package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.IdentityAccessEventRecoveryService;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/identity/access-event-recovery/tenants/{tenantId}")
@ConditionalOnProperty(
        prefix = "ainer.identity.access-event-recovery",
        name = "enabled",
        havingValue = "true")
public class IdentityAccessEventRecoveryController {

    private static final String READ = "SCOPE_identity.access-events.replay.read";
    private static final String READ_ALL = "SCOPE_identity.access-events.replay.read.all";
    private static final String REQUEST = "SCOPE_identity.access-events.replay.request";
    private static final String REQUEST_ALL = "SCOPE_identity.access-events.replay.request.all";
    private static final String APPROVE = "SCOPE_identity.access-events.replay.approve";
    private static final String APPROVE_ALL = "SCOPE_identity.access-events.replay.approve.all";

    private final IdentityAccessEventRecoveryService recoveryService;
    private final IdentityAccessEventRecoverySettings settings;
    private final Counter requestedCounter;
    private final Counter executedCounter;

    public IdentityAccessEventRecoveryController(
            IdentityAccessEventRecoveryService recoveryService,
            IdentityAccessEventRecoverySettings settings,
            MeterRegistry meterRegistry) {
        this.recoveryService = recoveryService;
        this.settings = settings;
        this.requestedCounter = meterRegistry.counter("ainer.identity.access.events.replay.requested");
        this.executedCounter = meterRegistry.counter("ainer.identity.access.events.replay.executed");
    }

    @GetMapping("/exhausted")
    public ApiResponse<IdentityAccessEventOutboxPageResponse> exhausted(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest request) {
        requireTenantAccess(authentication, tenantId, READ, READ_ALL);
        return ApiResponse.success(
                IdentityAccessEventOutboxPageResponse.from(
                        recoveryService.findExhausted(tenantId, page, size, settings.maxAttempts())),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/replay-requests")
    public ApiResponse<IdentityAccessEventReplayResponse> requestReplay(
            @PathVariable UUID tenantId,
            @Valid @RequestBody IdentityAccessEventReplayRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(
                authentication, tenantId, REQUEST, REQUEST_ALL);
        IdentityAccessEventReplayResponse response = IdentityAccessEventReplayResponse.from(
                recoveryService.requestReplay(
                        service.serviceId(), tenantId, body.eventId(), body.incidentReference(),
                        settings.approvalTtl(), settings.maxAttempts()));
        requestedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/replay-requests/{requestId}/approvals")
    public ApiResponse<IdentityAccessEventReplayResponse> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID requestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(
                authentication, tenantId, APPROVE, APPROVE_ALL);
        IdentityAccessEventReplayResponse response = IdentityAccessEventReplayResponse.from(
                recoveryService.approveAndExecute(
                        service.serviceId(), tenantId, requestId, settings.maxAttempts()));
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
}
