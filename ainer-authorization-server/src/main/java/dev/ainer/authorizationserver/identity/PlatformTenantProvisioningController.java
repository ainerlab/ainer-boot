package dev.ainer.authorizationserver.identity;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.CreateTenantProvisioningCommand;
import dev.ainer.module.identity.account.application.PlatformProvisioningActor;
import dev.ainer.module.identity.account.application.TenantProvisioningCancellationResult;
import dev.ainer.module.identity.account.application.TenantProvisioningRequest;
import dev.ainer.module.identity.account.application.TenantProvisioningResult;
import dev.ainer.module.identity.account.application.TenantProvisioningService;
import dev.ainer.security.AinerSecurityScopes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/platform/identity/tenant-provisioning-requests")
@ConditionalOnProperty(
        prefix = "ainer.identity.platform-control",
        name = "enabled",
        havingValue = "true")
public class PlatformTenantProvisioningController {

    private static final String TENANTS_READ =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_TENANTS_READ;
    private static final String TENANTS_WRITE =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_TENANTS_WRITE;
    private static final String USERS_READ =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_USERS_READ;
    private static final String USERS_WRITE =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_USERS_WRITE;

    private final TenantProvisioningService service;
    private final PlatformIdentityControlSettings settings;
    private final PlatformIdentityActorResolver actorResolver;
    private final Counter createdCounter;
    private final Counter idempotentCounter;
    private final Counter cancelledCounter;

    public PlatformTenantProvisioningController(
            TenantProvisioningService service,
            PlatformIdentityControlSettings settings,
            PlatformIdentityActorResolver actorResolver,
            MeterRegistry meterRegistry) {
        this.service = service;
        this.settings = settings;
        this.actorResolver = actorResolver;
        this.createdCounter =
                meterRegistry.counter("ainer.identity.tenant.provisioning.requested");
        this.idempotentCounter =
                meterRegistry.counter("ainer.identity.tenant.provisioning.idempotent");
        this.cancelledCounter =
                meterRegistry.counter("ainer.identity.tenant.provisioning.cancelled");
    }

    @PostMapping
    public ApiResponse<TenantProvisioningResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTenantProvisioningRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        PlatformProvisioningActor actor = actorResolver.require(
                authentication, request, TENANTS_WRITE, USERS_WRITE);
        TenantProvisioningResult result = service.create(
                new CreateTenantProvisioningCommand(
                        body.tenantCode(),
                        body.tenantName(),
                        body.ownerUsername(),
                        body.ownerDisplayName(),
                        body.deliveryChannel(),
                        body.deliveryAddress(),
                        idempotencyKey,
                        body.changeReference()),
                actor,
                settings.policy());
        if (result.created()) {
            createdCounter.increment();
        } else {
            idempotentCounter.increment();
        }
        return ApiResponse.success(
                TenantProvisioningResponse.from(result),
                actor.requestId());
    }

    @GetMapping("/{provisioningRequestId}")
    public ApiResponse<TenantProvisioningResponse> find(
            @PathVariable UUID provisioningRequestId,
            Authentication authentication,
            HttpServletRequest request) {
        PlatformProvisioningActor actor = actorResolver.require(
                authentication, request, TENANTS_READ, USERS_READ);
        return ApiResponse.success(
                TenantProvisioningResponse.from(
                        service.find(provisioningRequestId, actor), false),
                actor.requestId());
    }

    @PostMapping("/{provisioningRequestId}/cancellations")
    public ApiResponse<TenantProvisioningResponse> cancel(
            @PathVariable UUID provisioningRequestId,
            @Valid @RequestBody CancelTenantProvisioningRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        PlatformProvisioningActor actor = actorResolver.require(
                authentication, request, TENANTS_WRITE, USERS_WRITE);
        TenantProvisioningCancellationResult result =
                service.cancel(provisioningRequestId, body.changeReference(), actor);
        if (result.cancelled()) {
            cancelledCounter.increment();
        }
        return ApiResponse.success(
                TenantProvisioningResponse.from(result.request(), false),
                actor.requestId());
    }

    public record CreateTenantProvisioningRequest(
            @NotBlank
            @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9]")
            String tenantCode,
            @NotBlank @Size(min = 2, max = 80) String tenantName,
            @NotBlank
            @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._@-]{2,99}")
            String ownerUsername,
            @NotBlank @Size(max = 80) String ownerDisplayName,
            @NotBlank @Pattern(regexp = "(?i)EMAIL") String deliveryChannel,
            @NotBlank
            @Size(max = 320)
            @Pattern(regexp = "[^\\s@]{1,128}@[^\\s@]{1,190}")
            String deliveryAddress,
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,128}")
            String changeReference) {
    }

    public record CancelTenantProvisioningRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,128}")
            String changeReference) {
    }

    public record TenantProvisioningResponse(
            UUID id,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            UUID ownerSubjectId,
            String ownerUsername,
            String ownerDisplayName,
            boolean ownerUserExists,
            String status,
            String requestedByServiceId,
            String changeReference,
            Instant requestedAt,
            Instant expiresAt,
            Instant completedAt,
            boolean created) {

        static TenantProvisioningResponse from(TenantProvisioningResult result) {
            return from(result.request(), result.created());
        }

        static TenantProvisioningResponse from(
                TenantProvisioningRequest request,
                boolean created) {
            return new TenantProvisioningResponse(
                    request.id(),
                    request.tenantId(),
                    request.tenantCode(),
                    request.tenantName(),
                    request.ownerSubjectId(),
                    request.ownerUsername(),
                    request.ownerDisplayName(),
                    request.ownerUserExists(),
                    request.status(),
                    request.requestedByServiceId(),
                    request.changeReference(),
                    request.requestedAt(),
                    request.expiresAt(),
                    request.completedAt(),
                    created);
        }
    }
}
