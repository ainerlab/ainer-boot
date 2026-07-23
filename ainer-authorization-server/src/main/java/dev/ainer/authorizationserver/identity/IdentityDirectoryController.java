package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.IdentityDirectoryService;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/identity/directory")
@ConditionalOnProperty(
        prefix = "ainer.identity.directory-api",
        name = "enabled",
        havingValue = "true")
public class IdentityDirectoryController {

    private static final String DIRECTORY_READ = "SCOPE_identity.directory.read";
    private static final String DIRECTORY_READ_ALL = "SCOPE_identity.directory.read.all";

    private final IdentityDirectoryService directoryService;

    public IdentityDirectoryController(IdentityDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/tenants/{tenantId}/members/{subjectId}")
    public ApiResponse<IdentityDirectoryMemberResponse> findMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID subjectId,
            Authentication authentication,
            HttpServletRequest request) {
        requireTenantAccess(authentication, tenantId);
        IdentityDirectoryMemberResponse member = directoryService.findActiveMember(tenantId, subjectId)
                .map(IdentityDirectoryMemberResponse::from)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.DIRECTORY_MEMBER_NOT_FOUND));
        return ApiResponse.success(member, RequestIds.currentOrCreate(request));
    }

    @GetMapping("/tenants/{tenantId}/members")
    public ApiResponse<List<IdentityDirectoryMemberResponse>> searchMembers(
            @PathVariable UUID tenantId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            Authentication authentication,
            HttpServletRequest request) {
        requireTenantAccess(authentication, tenantId);
        List<IdentityDirectoryMemberResponse> members = directoryService
                .searchActiveMembers(tenantId, query, limit)
                .stream()
                .map(IdentityDirectoryMemberResponse::from)
                .toList();
        return ApiResponse.success(members, RequestIds.currentOrCreate(request));
    }

    private void requireTenantAccess(Authentication authentication, UUID requestedTenantId) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        if (service.hasAuthority(DIRECTORY_READ_ALL)) {
            return;
        }
        service.requireAuthority(DIRECTORY_READ);
        try {
            if (!requestedTenantId.equals(UUID.fromString(service.requireTenantId()))) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
