package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.OwnershipTransfer;
import dev.ainer.module.identity.account.application.OwnershipTransferService;
import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;
import dev.ainer.security.actor.AuthenticatedActor;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 租户所有权转移。见 ADR-0019 decision 24。正常转移使用双自然人确认状态机，
 * 通用成员接口不能授予、降级或移除 OWNER。
 */
@Validated
@RestController
@ConditionalOnProperty(
        prefix = "ainer.identity",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequestMapping("/api/tenants/{tenantId}/ownership-transfers")
public class OwnershipTransferController {

    private static final String SAFE_REFERENCE = "[A-Za-z0-9._:@/-]{1,128}";

    private final OwnershipTransferService transferService;

    public OwnershipTransferController(OwnershipTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ApiResponse<OwnershipTransferResponse> initiate(
            @PathVariable UUID tenantId,
            @Valid @RequestBody InitiateTransferRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        OwnershipTransfer transfer = transferService.initiateTransfer(
                actor, tenantId, request.targetSubjectId(), request.reasonCode(), requestId);
        return ApiResponse.success(OwnershipTransferResponse.from(transfer), requestId);
    }

    @GetMapping("/{transferId}")
    public ApiResponse<OwnershipTransferResponse> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID transferId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        OwnershipTransfer transfer = transferService.getTransfer(actor, tenantId, transferId);
        return ApiResponse.success(
                OwnershipTransferResponse.from(transfer),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{transferId}/acceptances")
    public ApiResponse<OwnershipTransferResponse> accept(
            @PathVariable UUID tenantId,
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferActionRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        OwnershipTransfer transfer = transferService.acceptTransfer(
                actor, tenantId, transferId, request.reasonCode(), requestId);
        return ApiResponse.success(OwnershipTransferResponse.from(transfer), requestId);
    }

    @PostMapping("/{transferId}/cancellations")
    public ApiResponse<OwnershipTransferResponse> cancel(
            @PathVariable UUID tenantId,
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferActionRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        OwnershipTransfer transfer = transferService.cancelTransfer(
                actor, tenantId, transferId, request.reasonCode(), requestId);
        return ApiResponse.success(OwnershipTransferResponse.from(transfer), requestId);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        Set<String> authorities = jwtAuthentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        try {
            return new AuthenticatedActor(
                    jwt.getSubject(),
                    jwt.getClaimAsString("tenant_id"),
                    jwt.getClaimAsString("actor_type"),
                    authorities);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    public record InitiateTransferRequest(
            @NotNull UUID targetSubjectId,
            @NotBlank @Pattern(regexp = SAFE_REFERENCE) String reasonCode) {
    }

    public record TransferActionRequest(
            @NotBlank @Pattern(regexp = SAFE_REFERENCE) String reasonCode) {
    }

    public record OwnershipTransferResponse(
            UUID id,
            UUID tenantId,
            UUID initiatorSubjectId,
            UUID targetSubjectId,
            OwnershipTransferStatus status,
            String reasonCode,
            Instant expiresAt,
            Instant createdAt,
            Instant executedAt,
            UUID executedBySubjectId) {

        static OwnershipTransferResponse from(OwnershipTransfer transfer) {
            return new OwnershipTransferResponse(
                    transfer.id(),
                    transfer.tenantId(),
                    transfer.initiatorSubjectId(),
                    transfer.targetSubjectId(),
                    transfer.status(),
                    transfer.reasonCode(),
                    transfer.expiresAt(),
                    transfer.createdAt(),
                    transfer.executedAt(),
                    transfer.executedBySubjectId());
        }
    }
}
