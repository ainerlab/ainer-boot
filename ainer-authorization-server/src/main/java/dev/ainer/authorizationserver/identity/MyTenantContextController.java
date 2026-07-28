package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.TenantContextEntry;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 当前 USER 的租户上下文查询。只返回该主体 ACTIVE membership 的安全投影；LOCKED/DISABLED
 * tenant、用户或 membership 不返回。见 ADR-0019 decision 16。
 */
@RestController
@ConditionalOnProperty(
        prefix = "ainer.identity",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequestMapping("/api/me/tenants")
public class MyTenantContextController {

    private final IdentityApplicationService identityService;

    public MyTenantContextController(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ApiResponse<List<TenantContextResponse>> list(
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        UUID subjectId = trustedUuid(actor.subjectId());
        List<TenantContextEntry> memberships =
                identityService.findActiveMemberships(subjectId);
        List<TenantContextResponse> response = memberships.stream()
                .map(TenantContextResponse::from)
                .toList();
        return ApiResponse.success(response, RequestIds.currentOrCreate(servletRequest));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        if (!"USER".equals(jwt.getClaimAsString("actor_type"))) {
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

    private static UUID trustedUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    public record TenantContextResponse(
            UUID tenantId,
            String tenantCode,
            String tenantName,
            TenantRole role,
            boolean defaultTenant) {

        static TenantContextResponse from(TenantContextEntry entry) {
            return new TenantContextResponse(
                    entry.tenantId(),
                    entry.tenantCode(),
                    entry.tenantName(),
                    entry.role(),
                    entry.defaultTenant());
        }
    }
}
