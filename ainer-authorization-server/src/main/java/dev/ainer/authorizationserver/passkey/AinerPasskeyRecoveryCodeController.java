package dev.ainer.authorizationserver.passkey;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Passkey 恢复码自助控制面（默认关闭）。见 ADR-0015。
 *
 * <p>签发 {@code POST /passkey/recovery-codes}：已登记账号需 WebAuthn 因子（由条件 MFA 门禁强制），
 * 返回一组明文恢复码（仅此一次）。赎回 {@code POST /passkey/recovery-codes/redeem}：密码登录本人
 * 提交一枚明文码，校验通过即吊销全部 ACTIVE Passkey；不要求 WebAuthn 因子（用户已丢失设备）。
 */
@Validated
@RestController
@RequestMapping("/passkey/recovery-codes")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.passkey.recovery",
        name = "self-service-enabled",
        havingValue = "true")
public class AinerPasskeyRecoveryCodeController {

    private final AinerPasskeyRecoveryCodeService recoveryCodeService;
    private final AinerUserDetailsService userDetailsService;

    public AinerPasskeyRecoveryCodeController(
            AinerPasskeyRecoveryCodeService recoveryCodeService,
            AinerUserDetailsService userDetailsService) {
        this.recoveryCodeService = recoveryCodeService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping
    public ApiResponse<RecoveryCodeIssuanceResponse> issue(Authentication authentication, HttpServletRequest request) {
        AinerUserDetails user = requireUser(authentication);
        AinerPasskeyRecoveryCodeService.RecoveryCodeIssuance issuance = user.accountId() != null
                && !user.hasLegacyTenantContext()
                ? recoveryCodeService.issueForAccount(user.accountId())
                : recoveryCodeService.issue(user.tenantId(), user.subjectId());
        return ApiResponse.success(
                new RecoveryCodeIssuanceResponse(issuance.operationId(), issuance.plaintextCodes()),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/redeem")
    public ApiResponse<RedeemResponse> redeem(
            @Valid @RequestBody RedeemRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AinerUserDetails user = requireUser(authentication);
        boolean redeemed = user.accountId() != null && !user.hasLegacyTenantContext()
                ? recoveryCodeService.redeemForAccount(user.accountId(), body.code())
                : recoveryCodeService.redeem(user.tenantId(), user.subjectId(), body.code());
        return ApiResponse.success(new RedeemResponse(redeemed), RequestIds.currentOrCreate(request));
    }

    private AinerUserDetails requireUser(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AinerUserDetails user) {
            return user;
        }
        if (principal instanceof PublicKeyCredentialUserEntity webAuthnUser) {
            org.springframework.security.core.userdetails.UserDetails loaded =
                    userDetailsService.loadUserByUsername(webAuthnUser.getName());
            if (loaded instanceof AinerUserDetails ainerUser) {
                return ainerUser;
            }
        }
        throw new IllegalStateException("Ainer passkey recovery requires an authenticated account");
    }

    public record RecoveryCodeIssuanceResponse(UUID operationId, List<String> recoveryCodes) {
    }

    public record RedeemRequest(@NotBlank String code) {
    }

    public record RedeemResponse(boolean redeemed) {
    }
}
