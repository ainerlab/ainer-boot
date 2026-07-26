package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.TenantProvisioningCompletion;
import dev.ainer.module.identity.account.application.TenantProvisioningService;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@ConditionalOnProperty(
        prefix = "ainer.identity.platform-control",
        name = "enabled",
        havingValue = "true")
public class TenantProvisioningActivationController {

    private static final String ACCEPT_AUTHORITY =
            "SCOPE_" + AinerSecurityScopes.IDENTITY_PROVISIONING_ACCEPT;

    private final TenantProvisioningService service;

    public TenantProvisioningActivationController(
            TenantProvisioningService service) {
        this.service = service;
    }

    @PostMapping("/api/identity/tenant-activations/{grantId}/consumptions")
    public ApiResponse<ActivationResponse> activateNewUser(
            @PathVariable UUID grantId,
            @Valid @RequestBody ConsumeActivationRequest body,
            HttpServletRequest request) {
        String requestId = RequestIds.currentOrCreate(request);
        TenantProvisioningCompletion completion = service.activateNewUser(
                grantId,
                body.activationSecret(),
                body.password(),
                requestId);
        return successOrThrow(completion, requestId);
    }

    @PostMapping("/api/me/tenant-provisioning-requests/{provisioningRequestId}/acceptances")
    public ApiResponse<ActivationResponse> acceptExistingUser(
            @PathVariable UUID provisioningRequestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedActor actor = actor(authentication);
        actor.requireAuthority(ACCEPT_AUTHORITY);
        String requestId = RequestIds.currentOrCreate(request);
        TenantProvisioningCompletion completion = service.acceptExistingUser(
                provisioningRequestId,
                parseSubject(actor.subjectId()),
                requestId);
        return successOrThrow(completion, requestId);
    }

    private ApiResponse<ActivationResponse> successOrThrow(
            TenantProvisioningCompletion completion,
            String requestId) {
        if (!completion.activated()) {
            throw new BusinessException(completion.failure());
        }
        return ApiResponse.success(
                new ActivationResponse(
                        completion.request().id(),
                        completion.identity().tenantId(),
                        completion.identity().subjectId(),
                        completion.request().status(),
                        completion.request().completedAt()),
                requestId);
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
            AuthenticatedActor actor = new AuthenticatedActor(
                    jwt.getSubject(),
                    jwt.getClaimAsString("tenant_id"),
                    jwt.getClaimAsString("actor_type"),
                    authorities);
            if (!actor.isUser()) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
            return actor;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private UUID parseSubject(String subjectId) {
        try {
            return UUID.fromString(subjectId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    public record ConsumeActivationRequest(
            @NotBlank @Size(min = 43, max = 128) String activationSecret,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record ActivationResponse(
            UUID provisioningRequestId,
            UUID tenantId,
            UUID subjectId,
            String status,
            Instant activatedAt) {
    }
}
