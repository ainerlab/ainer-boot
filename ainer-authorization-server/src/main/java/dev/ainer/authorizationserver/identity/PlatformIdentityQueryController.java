package dev.ainer.authorizationserver.identity;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.PlatformIdentityQueryService;
import dev.ainer.module.identity.account.application.PlatformIdentityTenantPage;
import dev.ainer.module.identity.account.application.PlatformIdentityTenantProjection;
import dev.ainer.module.identity.account.application.PlatformIdentityUserPage;
import dev.ainer.module.identity.account.application.PlatformIdentityUserProjection;
import dev.ainer.module.identity.account.application.PlatformProvisioningActor;
import dev.ainer.security.AinerSecurityScopes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/platform/identity")
@ConditionalOnProperty(
        prefix = "ainer.identity.platform-control",
        name = "enabled",
        havingValue = "true")
public class PlatformIdentityQueryController {

    private static final String TENANTS_READ =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_TENANTS_READ;
    private static final String USERS_READ =
            "SCOPE_" + AinerSecurityScopes.PLATFORM_USERS_READ;

    private final PlatformIdentityQueryService service;
    private final PlatformIdentityActorResolver actorResolver;

    public PlatformIdentityQueryController(
            PlatformIdentityQueryService service,
            PlatformIdentityActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/tenants")
    public ApiResponse<TenantPageResponse> tenants(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest request) {
        PlatformProvisioningActor actor =
                actorResolver.require(authentication, request, TENANTS_READ);
        return ApiResponse.success(
                TenantPageResponse.from(service.tenants(actor, page, size)),
                actor.requestId());
    }

    @GetMapping("/users")
    public ApiResponse<UserPageResponse> users(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest request) {
        PlatformProvisioningActor actor =
                actorResolver.require(authentication, request, USERS_READ);
        return ApiResponse.success(
                UserPageResponse.from(service.users(actor, page, size)),
                actor.requestId());
    }

    public record TenantPageResponse(
            List<TenantResponse> items,
            int page,
            int size,
            long total) {

        static TenantPageResponse from(PlatformIdentityTenantPage result) {
            return new TenantPageResponse(
                    result.items().stream().map(TenantResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.total());
        }
    }

    public record TenantResponse(
            UUID id,
            String code,
            String name,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        static TenantResponse from(PlatformIdentityTenantProjection tenant) {
            return new TenantResponse(
                    tenant.id(),
                    tenant.code(),
                    tenant.name(),
                    tenant.status().name(),
                    tenant.createdAt(),
                    tenant.updatedAt());
        }
    }

    public record UserPageResponse(
            List<UserResponse> items,
            int page,
            int size,
            long total) {

        static UserPageResponse from(PlatformIdentityUserPage result) {
            return new UserPageResponse(
                    result.items().stream().map(UserResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.total());
        }
    }

    public record UserResponse(
            UUID subjectId,
            String username,
            String displayName,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        static UserResponse from(PlatformIdentityUserProjection user) {
            return new UserResponse(
                    user.subjectId(),
                    user.username(),
                    user.displayName(),
                    user.status().name(),
                    user.createdAt(),
                    user.updatedAt());
        }
    }
}
