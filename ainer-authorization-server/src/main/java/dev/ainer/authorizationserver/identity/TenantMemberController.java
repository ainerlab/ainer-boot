package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.AddTenantMemberCommand;
import dev.ainer.module.identity.account.application.MemberPage;
import dev.ainer.module.identity.account.application.MemberSummary;
import dev.ainer.module.identity.account.application.TenantMemberManagementService;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 租户内成员管理（USER token + capability scope + 租户资源角色）。通用接口不能授予、降级或移除
 * OWNER；OWNER 变更必须走专用所有权转移用例。
 */
@Validated
@RestController
@ConditionalOnProperty(
        prefix = "ainer.identity",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequestMapping("/api/tenants/{tenantId}/members")
public class TenantMemberController {

    private static final String SAFE_REASON = "[A-Za-z0-9._:@/-]{1,128}";

    private final TenantMemberManagementService memberService;

    public TenantMemberController(TenantMemberManagementService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<MemberPageResponse> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        MemberPage pageResult = memberService.listMembers(actor, tenantId, page, size);
        return ApiResponse.success(
                MemberPageResponse.from(pageResult),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping
    public ApiResponse<MemberResponse> add(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AddTenantMemberRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        MemberSummary member = memberService.addMember(
                actor,
                tenantId,
                new AddTenantMemberCommand(
                        request.username(), request.subjectId(), request.role(), request.reasonCode()),
                requestId);
        return ApiResponse.success(MemberResponse.from(member), requestId);
    }

    @PatchMapping("/{subjectId}")
    public ApiResponse<MemberResponse> changeRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID subjectId,
            @Valid @RequestBody ChangeTenantMemberRoleRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        MemberSummary member = memberService.changeMemberRole(
                actor, tenantId, subjectId, request.role(), request.reasonCode(), requestId);
        return ApiResponse.success(MemberResponse.from(member), requestId);
    }

    @DeleteMapping("/{subjectId}")
    public ApiResponse<Void> remove(
            @PathVariable UUID tenantId,
            @PathVariable UUID subjectId,
            @RequestParam @NotBlank @Pattern(regexp = SAFE_REASON) String reasonCode,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(authentication);
        String requestId = RequestIds.currentOrCreate(servletRequest);
        memberService.removeMember(actor, tenantId, subjectId, reasonCode, requestId);
        return ApiResponse.success(null, requestId);
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

    public record AddTenantMemberRequest(
            String username,
            UUID subjectId,
            @NotNull TenantRole role,
            @NotBlank @Pattern(regexp = SAFE_REASON) String reasonCode) {
    }

    public record ChangeTenantMemberRoleRequest(
            @NotNull TenantRole role,
            @NotBlank @Pattern(regexp = SAFE_REASON) String reasonCode) {
    }

    public record MemberResponse(
            UUID subjectId,
            String username,
            String displayName,
            TenantRole role) {

        static MemberResponse from(MemberSummary member) {
            return new MemberResponse(
                    member.subjectId(), member.username(), member.displayName(), member.role());
        }
    }

    public record MemberPageResponse(
            List<MemberResponse> members,
            int page,
            int size,
            int total) {

        static MemberPageResponse from(MemberPage page) {
            return new MemberPageResponse(
                    page.members().stream().map(MemberResponse::from).toList(),
                    page.page(),
                    page.size(),
                    page.total());
        }
    }
}
