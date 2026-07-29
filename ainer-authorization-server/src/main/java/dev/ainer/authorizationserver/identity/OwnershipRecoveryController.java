package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.OwnershipRecovery;
import dev.ainer.module.identity.account.application.OwnershipRecoveryService;
import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * OWNER 丢失恢复。见 ADR-0019 decision 30。tenantless SERVICE request/approve，
 * 不同 service subject；与正常转移不共用端点或授权规则。
 */
@Validated
@RestController
@ConditionalOnProperty(
        prefix = "ainer.identity",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequestMapping("/internal/identity/ownership-recovery")
public class OwnershipRecoveryController {

    private static final String SAFE_REFERENCE = "[A-Za-z0-9._:@/-]{1,128}";

    private final OwnershipRecoveryService recoveryService;

    public OwnershipRecoveryController(OwnershipRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping
    public ApiResponse<OwnershipRecoveryResponse> request(
            @Valid @RequestBody RequestRecoveryRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        ServiceActor actor = serviceActor(authentication, "identity.ownership-recovery.request");
        OwnershipRecovery recovery = recoveryService.requestRecovery(
                actor.serviceId(), body.tenantId(), body.targetSubjectId(), body.incidentReference());
        return ApiResponse.success(
                OwnershipRecoveryResponse.from(recovery),
                RequestIds.currentOrCreate(servletRequest));
    }

    @GetMapping("/{recoveryId}")
    public ApiResponse<OwnershipRecoveryResponse> get(
            @PathVariable UUID recoveryId,
            @org.springframework.web.bind.annotation.RequestParam UUID tenantId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        ServiceActor actor = serviceActor(authentication,
                "identity.ownership-recovery.request", "identity.ownership-recovery.approve");
        OwnershipRecovery recovery = recoveryService.getRecovery(
                actor.serviceId(), tenantId, recoveryId);
        return ApiResponse.success(
                OwnershipRecoveryResponse.from(recovery),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{recoveryId}/approvals")
    public ApiResponse<OwnershipRecoveryResponse> approve(
            @PathVariable UUID recoveryId,
            @Valid @RequestBody RecoveryActionRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        ServiceActor actor = serviceActor(authentication, "identity.ownership-recovery.approve");
        OwnershipRecovery recovery = recoveryService.approveAndExecute(
                actor.serviceId(), body.tenantId(), recoveryId);
        return ApiResponse.success(
                OwnershipRecoveryResponse.from(recovery),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{recoveryId}/cancellations")
    public ApiResponse<OwnershipRecoveryResponse> cancel(
            @PathVariable UUID recoveryId,
            @Valid @RequestBody RecoveryActionRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        ServiceActor actor = serviceActor(authentication, "identity.ownership-recovery.request");
        OwnershipRecovery recovery = recoveryService.cancelRecovery(
                actor.serviceId(), body.tenantId(), recoveryId);
        return ApiResponse.success(
                OwnershipRecoveryResponse.from(recovery),
                RequestIds.currentOrCreate(servletRequest));
    }

    private static ServiceActor serviceActor(Authentication authentication, String... requiredScopes) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !(jwtAuth.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        if (!"SERVICE".equals(jwt.getClaimAsString("actor_type"))) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        var authorities = jwtAuth.getAuthorities();
        for (String scope : requiredScopes) {
            boolean found = authorities.stream()
                    .anyMatch(a -> ("SCOPE_" + scope).equals(a.getAuthority()));
            if (!found) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
        }
        return new ServiceActor(sub);
    }

    private record ServiceActor(String serviceId) {}

    public record RequestRecoveryRequest(
            @NotNull UUID tenantId,
            @NotNull UUID targetSubjectId,
            @NotBlank @Pattern(regexp = SAFE_REFERENCE) String incidentReference) {
    }

    public record RecoveryActionRequest(
            @NotNull UUID tenantId) {
    }

    public record OwnershipRecoveryResponse(
            UUID id,
            UUID tenantId,
            UUID targetSubjectId,
            OwnershipTransferStatus status,
            String requesterServiceId,
            String approverServiceId,
            String incidentReference,
            Instant expiresAt,
            Instant executedAt) {

        static OwnershipRecoveryResponse from(OwnershipRecovery recovery) {
            return new OwnershipRecoveryResponse(
                    recovery.id(),
                    recovery.tenantId(),
                    recovery.targetSubjectId(),
                    recovery.status(),
                    recovery.requesterServiceId(),
                    recovery.approverServiceId(),
                    recovery.incidentReference(),
                    recovery.expiresAt(),
                    recovery.executedAt());
        }
    }
}
