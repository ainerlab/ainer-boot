package dev.ainer.authorizationserver.config;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.foundation.HumanAccount;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.ServicePrincipal;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.security.token.TokenProfile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Access-token claim projection for the Ainer authorization server (S3 of the Greenfield cutover).
 *
 * <p>Clients opt into a Greenfield token profile via the {@code ainer.token-profile} client setting.
 * {@code SERVICE_V1} projects a {@code ServicePrincipal} (stable audit identity behind a rotatable
 * {@code client_id}); {@code USER_NEUTRAL_V1} projects a foundation {@code HumanAccount}. Both carry
 * {@code token_profile}, {@code claim_contract_version} and the principal's live {@code sec_epoch}, and
 * fail closed when the backing principal cannot be resolved or is not active. Clients without the setting
 * keep the legacy tenant-bound claim contract untouched.
 */
public class AinerJwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final AinerAuthorizationServerProperties properties;
    private final AinerUserDetailsService userDetailsService;
    private final IdentityApplicationService identityService;
    private final ServicePrincipalFoundationService servicePrincipalFoundationService;
    private final HumanAccountRepository humanAccountRepository;

    public AinerJwtTokenCustomizer(
            AinerAuthorizationServerProperties properties,
            AinerUserDetailsService userDetailsService,
            IdentityApplicationService identityService,
            ServicePrincipalFoundationService servicePrincipalFoundationService,
            HumanAccountRepository humanAccountRepository) {
        this.properties = properties;
        this.userDetailsService = userDetailsService;
        this.identityService = identityService;
        this.servicePrincipalFoundationService = servicePrincipalFoundationService;
        this.humanAccountRepository = humanAccountRepository;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            String audience = properties.getAudience();
            if (audience == null || audience.isBlank()) {
                throw new IllegalStateException("Ainer authorization access-token audience is required");
            }
            context.getClaims().audience(new ArrayList<>(List.of(audience)));
        }
        RegisteredClient client = context.getRegisteredClient();
        Object profileSetting = client == null
                ? null
                : client.getClientSettings()
                        .getSetting(AinerAuthorizationServerConfiguration.TOKEN_PROFILE_SETTING);
        String tokenProfile = profileSetting == null ? null : profileSetting.toString();
        if (tokenProfile == null || tokenProfile.isBlank()) {
            applyLegacy(context);
            return;
        }
        if (TokenProfile.SERVICE_V1.claimValue().equals(tokenProfile)) {
            applyServiceV1(context);
            return;
        }
        if (TokenProfile.USER_NEUTRAL_V1.claimValue().equals(tokenProfile)) {
            applyUserNeutralV1(context);
            return;
        }
        throw failedClosed("Unknown token profile: " + tokenProfile);
    }

    private void applyServiceV1(JwtEncodingContext context) {
        RegisteredClient client = context.getRegisteredClient();
        String clientId = client.getClientId();
        ServicePrincipal principal = servicePrincipalFoundationService.findPrincipalByClientId(clientId)
                .orElseThrow(() -> failedClosed(
                        "No ACTIVE ServicePrincipal bound to client " + clientId));
        if (!principal.status().canAuthenticate()) {
            throw failedClosed(
                    "ServicePrincipal " + principal.principalId() + " is not active");
        }
        context.getClaims()
                .subject(principal.principalId().toString())
                .claim(TokenProfile.PROFILE_CLAIM, TokenProfile.SERVICE_V1.claimValue())
                .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                .claim("actor_type", "SERVICE")
                .claim(AinerAuthorizationServerConfiguration.SEC_EPOCH_CLAIM, principal.securityEpoch());
    }

    private void applyUserNeutralV1(JwtEncodingContext context) {
        Authentication authentication = context.getPrincipal();
        AinerUserDetails user = ainerUserDetails(authentication);
        if (user == null) {
            throw failedClosed("USER_NEUTRAL_V1 token profile requires a user principal");
        }
        UUID accountId = user.accountId();
        if (accountId == null) {
            throw failedClosed(
                    "USER_NEUTRAL_V1 token profile requires a foundation account");
        }
        HumanAccount account = humanAccountRepository.findByAccountId(accountId)
                .orElseThrow(() -> failedClosed(
                        "HumanAccount not found for USER_NEUTRAL_V1 token: " + accountId));
        if (!account.status().canAuthenticate()) {
            throw failedClosed("HumanAccount " + accountId + " is not active");
        }
        JwtClaimsSet.Builder claims = context.getClaims();
        claims.subject(account.accountId().toString())
                .claim(TokenProfile.PROFILE_CLAIM, TokenProfile.USER_NEUTRAL_V1.claimValue())
                .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                .claim("actor_type", "USER")
                .claim(AinerAuthorizationServerConfiguration.SEC_EPOCH_CLAIM, account.securityEpoch());
        List<FactorGrantedAuthority> factors = authentication.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast)
                .toList();
        List<String> authenticationMethods = authenticationMethods(factors);
        if (!authenticationMethods.isEmpty()) {
            claims.claim(IdTokenClaimNames.AMR, new ArrayList<>(authenticationMethods));
            factors.stream()
                    .map(FactorGrantedAuthority::getIssuedAt)
                    .max(Instant::compareTo)
                    .ifPresent(authTime -> claims.claim(
                            IdTokenClaimNames.AUTH_TIME, Date.from(authTime)));
        }
    }

    private void applyLegacy(JwtEncodingContext context) {
        Authentication authentication = context.getPrincipal();
        AinerUserDetails user = ainerUserDetails(authentication);
        if (user != null) {
            if (!user.hasLegacyTenantContext()) {
                throw failedClosed("Legacy token profile requires a tenant-bound user principal");
            }
            // M4.8B：tenant claim 来自 Identity 实时关系，不直接信任登录时缓存的 principal。
            // principal 中的 tenantId 可能是默认落点，也可能是租户选择后更新过的值；customizer
            // 再次读取 membership 校验该关系仍然 ACTIVE 并取得当前角色。
            IdentityDirectoryEntry membership = identityService.findActiveMembership(
                    user.tenantId(), user.subjectId()).orElseThrow(() ->
                    new IllegalStateException(
                            "Active membership not found for subject " + user.subjectId()
                                    + " in tenant " + user.tenantId()));
            context.getClaims()
                    .subject(user.subjectId().toString())
                    .claim("actor_type", "USER")
                    .claim("tenant_id", membership.tenantId().toString())
                    .claim("roles", new ArrayList<>(List.of(membership.role().name())));
            List<FactorGrantedAuthority> factors = authentication.getAuthorities().stream()
                    .filter(FactorGrantedAuthority.class::isInstance)
                    .map(FactorGrantedAuthority.class::cast)
                    .toList();
            List<String> authenticationMethods = authenticationMethods(factors);
            if (!authenticationMethods.isEmpty()) {
                context.getClaims().claim(
                        IdTokenClaimNames.AMR,
                        new ArrayList<>(authenticationMethods));
                factors.stream()
                        .map(FactorGrantedAuthority::getIssuedAt)
                        .max(Instant::compareTo)
                        .ifPresent(authTime -> context.getClaims().claim(
                                IdTokenClaimNames.AUTH_TIME,
                                Date.from(authTime)));
            }
            return;
        }
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            RegisteredClient client = context.getRegisteredClient();
            context.getClaims()
                    .subject(client.getClientId())
                    .claim("actor_type", "SERVICE");
            String tenantId = client.getClientSettings()
                    .getSetting(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING);
            if (tenantId != null && !tenantId.isBlank()) {
                context.getClaims().claim("tenant_id", tenantId);
            }
        }
    }

    private AinerUserDetails ainerUserDetails(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AinerUserDetails user) {
            return user;
        }
        // Passkey 用户经 WebAuthn 认证后，主体是协议 PublicKeyCredentialUserEntity（只含 username），
        // 需经 Identity 解析为 AinerUserDetails 才能投影稳定 sub/tenant_id/roles。
        if (principal instanceof PublicKeyCredentialUserEntity webAuthnUser) {
            UserDetails loaded = userDetailsService.loadUserByUsername(webAuthnUser.getName());
            if (loaded instanceof AinerUserDetails ainerUser) {
                return ainerUser;
            }
        }
        return null;
    }

    private List<String> authenticationMethods(List<FactorGrantedAuthority> factors) {
        Set<String> methods = new LinkedHashSet<>();
        if (hasFactor(factors, FactorGrantedAuthority.PASSWORD_AUTHORITY)) {
            methods.add("pwd");
        }
        if (hasFactor(factors, FactorGrantedAuthority.WEBAUTHN_AUTHORITY)) {
            methods.add("mfa");
            methods.add("pop");
        }
        return List.copyOf(methods);
    }

    private boolean hasFactor(List<FactorGrantedAuthority> factors, String authority) {
        return factors.stream()
                .anyMatch(factor -> authority.equals(factor.getAuthority()));
    }

    /**
     * Fail-closed projection failure: the declared token profile cannot be backed by an active principal.
     * Thrown as an OAuth2 error so the token endpoint rejects the request instead of issuing a token.
     */
    private OAuth2AuthenticationException failedClosed(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.ACCESS_DENIED, message, null));
    }
}
