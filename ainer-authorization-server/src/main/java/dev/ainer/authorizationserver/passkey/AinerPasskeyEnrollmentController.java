package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Passkey enrollment 授权控制面（默认关闭，由 /internal 安全链 + scope 强制 SERVICE）。见 ADR-0016。
 */
@Validated
@RestController
@RequestMapping("/internal/passkey-enrollment")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.passkey",
        name = "enabled",
        havingValue = "true")
public class AinerPasskeyEnrollmentController {

    private static final String MANAGE = "SCOPE_passkey.enrollment.manage";
    private static final String MANAGE_ALL = "SCOPE_passkey.enrollment.manage.all";

    private final AinerPasskeyEnrollmentGrantService grantService;

    public AinerPasskeyEnrollmentController(AinerPasskeyEnrollmentGrantService grantService) {
        this.grantService = grantService;
    }

    @PostMapping("/tenants/{tenantId}/grants")
    public ApiResponse<EnrollmentGrantResponse> grant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody EnrollmentGrantRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(authentication, tenantId);
        EnrollmentGrantResponse response = EnrollmentGrantResponse.from(
                grantService.grant(service.serviceId(), tenantId, body.subjectId(), body.incidentReference()));
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @GetMapping("/tenants/{tenantId}/grants")
    public ApiResponse<List<EnrollmentGrantResponse>> list(
            @PathVariable UUID tenantId,
            Authentication authentication,
            HttpServletRequest request) {
        requireTenantAccess(authentication, tenantId);
        List<EnrollmentGrantResponse> grants = grantService.findGrants(tenantId).stream()
                .map(EnrollmentGrantResponse::from)
                .toList();
        return ApiResponse.success(grants, RequestIds.currentOrCreate(request));
    }

    @DeleteMapping("/tenants/{tenantId}/grants/{subjectId}")
    public ApiResponse<EnrollmentGrantResponse> revoke(
            @PathVariable UUID tenantId,
            @PathVariable UUID subjectId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireTenantAccess(authentication, tenantId);
        EnrollmentGrantResponse response = EnrollmentGrantResponse.from(
                grantService.revoke(service.serviceId(), tenantId, subjectId));
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/accounts/{accountId}/grants")
    public ApiResponse<AccountEnrollmentGrantResponse> grantAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountEnrollmentGrantRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAllAccess(authentication);
        AccountEnrollmentGrantResponse response = AccountEnrollmentGrantResponse.from(
                grantService.grantForAccount(service.serviceId(), accountId, body.incidentReference()));
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @GetMapping("/accounts/{accountId}/grants")
    public ApiResponse<List<AccountEnrollmentGrantResponse>> listAccount(
            @PathVariable UUID accountId,
            Authentication authentication,
            HttpServletRequest request) {
        requireAllAccess(authentication);
        List<AccountEnrollmentGrantResponse> grants = grantService.findGrantsForAccount(accountId).stream()
                .map(AccountEnrollmentGrantResponse::from)
                .toList();
        return ApiResponse.success(grants, RequestIds.currentOrCreate(request));
    }

    @DeleteMapping("/accounts/{accountId}/grants")
    public ApiResponse<AccountEnrollmentGrantResponse> revokeAccount(
            @PathVariable UUID accountId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAllAccess(authentication);
        AccountEnrollmentGrantResponse response = AccountEnrollmentGrantResponse.from(
                grantService.revokeForAccount(service.serviceId(), accountId));
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireTenantAccess(Authentication authentication, UUID tenantId) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        if (service.hasAuthority(MANAGE_ALL)) {
            return service;
        }
        service.requireAuthority(MANAGE);
        try {
            if (!tenantId.equals(UUID.fromString(service.requireTenantId()))) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return service;
    }

    private AuthenticatedService requireAllAccess(Authentication authentication) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(MANAGE_ALL);
        return service;
    }

    public record EnrollmentGrantRequestBody(@NotNull UUID subjectId, @NotNull String incidentReference) {
    }

    public record EnrollmentGrantResponse(
            UUID subjectId, UUID tenantId, String grantedBy, String incidentReference,
            String status, String grantedAt, String consumedAt) {
        static EnrollmentGrantResponse from(AinerPasskeyEnrollmentGrantService.EnrollmentGrant grant) {
            return new EnrollmentGrantResponse(
                    grant.subjectId(), grant.tenantId(), grant.grantedBy(),
                    grant.incidentReference(), grant.status(),
                    grant.grantedAt().toString(),
                    grant.consumedAt() == null ? null : grant.consumedAt().toString());
        }
    }

    public record AccountEnrollmentGrantRequestBody(@NotNull String incidentReference) {
    }

    public record AccountEnrollmentGrantResponse(
            UUID accountId, String grantedBy, String incidentReference,
            String status, String grantedAt, String consumedAt) {
        static AccountEnrollmentGrantResponse from(
                AinerPasskeyEnrollmentGrantService.AccountEnrollmentGrant grant) {
            return new AccountEnrollmentGrantResponse(
                    grant.accountId(), grant.grantedBy(), grant.incidentReference(), grant.status(),
                    grant.grantedAt().toString(),
                    grant.consumedAt() == null ? null : grant.consumedAt().toString());
        }
    }
}
