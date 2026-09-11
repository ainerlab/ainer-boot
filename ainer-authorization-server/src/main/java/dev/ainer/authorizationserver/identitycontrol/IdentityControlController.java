package dev.ainer.authorizationserver.identitycontrol;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.foundation.AccountStatus;
import dev.ainer.module.identity.foundation.CredentialType;
import dev.ainer.module.identity.foundation.ServicePrincipalStatus;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 身份生命周期控制面（默认关闭）。放在 Authorization Server 发行物内，与 Identity 的
 * "HTTP adapter 位于 ainer-authorization-server" 边界一致：Identity 模块本身没有 Web 依赖。
 *
 * <p>每条路径都要求类型化 SERVICE Token（{@code actor_type=SERVICE}，由内部安全过滤链
 * 先完成 JWT 认证）+ 各自的最小 scope，再叠加可信 {@code sub} 白名单与 ServicePrincipal
 * 当前 epoch 校验（见 {@link IdentityControlService}）。没有匿名可达入口，也没有绕过
 * 既有授权机制的旁路：未登记的路径在 {@code /internal/**} 过滤链里仍然
 * {@code anyRequest().denyAll()}。
 */
@Validated
@RestController
@RequestMapping("/internal/identity")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.identity-control",
        name = "enabled",
        havingValue = "true")
public class IdentityControlController {

    private static final String ACCOUNT_MANAGE_AUTHORITY =
            "SCOPE_" + AinerAuthorizationServerConfiguration.IDENTITY_ACCOUNT_CONTROL_MANAGE_SCOPE;
    private static final String SERVICE_PRINCIPAL_MANAGE_AUTHORITY =
            "SCOPE_" + AinerAuthorizationServerConfiguration
                    .IDENTITY_SERVICE_PRINCIPAL_CONTROL_MANAGE_SCOPE;

    private final IdentityControlService controlService;

    public IdentityControlController(IdentityControlService controlService) {
        this.controlService = controlService;
    }

    /** 人员账号状态迁移：DISABLED（禁用）/ LOCKED（锁定）/ CLOSED（关闭）/ ACTIVE（恢复）。 */
    @PostMapping("/accounts/{accountId}/status-transitions")
    public ApiResponse<IdentityControlService.LifecycleView> transitionAccountStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountStatusTransitionRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAuthority(authentication, ACCOUNT_MANAGE_AUTHORITY);
        IdentityControlService.ControlActor actor = actor(authentication, service, request);
        return ApiResponse.success(
                controlService.transitionAccountStatus(
                        accountId, body.status(), body.changeReference(), actor),
                actor.requestId());
    }

    /** 密码轮换：吊销旧密码材料、写入新材料并递增 epoch，响应不回显任何密码。 */
    @PostMapping("/accounts/{accountId}/password-rotations")
    public ApiResponse<IdentityControlService.LifecycleView> rotatePassword(
            @PathVariable UUID accountId,
            @Valid @RequestBody PasswordRotationRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAuthority(authentication, ACCOUNT_MANAGE_AUTHORITY);
        IdentityControlService.ControlActor actor = actor(authentication, service, request);
        return ApiResponse.success(
                controlService.rotatePassword(
                        accountId, body.newPassword(), body.changeReference(), actor),
                actor.requestId());
    }

    /** 凭据撤销：吊销指定类型的 ACTIVE 凭据材料并递增 epoch（凭据泄漏/丢失处置）。 */
    @PostMapping("/accounts/{accountId}/credential-revocations")
    public ApiResponse<IdentityControlService.LifecycleView> revokeCredential(
            @PathVariable UUID accountId,
            @Valid @RequestBody CredentialRevocationRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAuthority(authentication, ACCOUNT_MANAGE_AUTHORITY);
        IdentityControlService.ControlActor actor = actor(authentication, service, request);
        return ApiResponse.success(
                controlService.revokeCredential(
                        accountId, body.credentialType(), body.changeReference(), actor),
                actor.requestId());
    }

    /** 服务主体状态迁移：DISABLED（禁用）/ ACTIVE（恢复），使用独立 scope。 */
    @PostMapping("/service-principals/{principalId}/status-transitions")
    public ApiResponse<IdentityControlService.LifecycleView> transitionServicePrincipalStatus(
            @PathVariable UUID principalId,
            @Valid @RequestBody ServicePrincipalStatusTransitionRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service =
                requireAuthority(authentication, SERVICE_PRINCIPAL_MANAGE_AUTHORITY);
        IdentityControlService.ControlActor actor = actor(authentication, service, request);
        return ApiResponse.success(
                controlService.transitionServicePrincipalStatus(
                        principalId, body.status(), body.changeReference(), actor),
                actor.requestId());
    }

    private static AuthenticatedService requireAuthority(
            Authentication authentication, String authority) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(authority);
        return service;
    }

    /**
     * 构造控制面调用方投影。缺失/非法 {@code sec_epoch} claim 的 SERVICE Token 失败关闭：
     * 没有 epoch 就无法证明调用方主体未被禁用或轮换。
     */
    private static IdentityControlService.ControlActor actor(
            Authentication authentication, AuthenticatedService service, HttpServletRequest request) {
        Object claim = authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaims().get(AinerAuthorizationServerConfiguration.SEC_EPOCH_CLAIM)
                : null;
        if (!(claim instanceof Number number) || number.longValue() < 0
                || Double.compare(number.doubleValue(), number.longValue()) != 0) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return new IdentityControlService.ControlActor(
                service.serviceId(), number.longValue(), RequestIds.currentOrCreate(request));
    }

    public record AccountStatusTransitionRequest(
            @NotNull AccountStatus status,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }

    public record ServicePrincipalStatusTransitionRequest(
            @NotNull ServicePrincipalStatus status,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }

    public record PasswordRotationRequest(
            @NotBlank @Size(min = 12, max = 128) String newPassword,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }

    public record CredentialRevocationRequest(
            @NotNull CredentialType credentialType,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }
}
