package dev.ainer.authorizationserver.config;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
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

/**
 * Access-token claim projection for the Ainer authorization server (S3 of the Greenfield cutover).
 *
 * <p>Clients declare a Greenfield token profile via the {@code ainer.token-profile} client setting.
 * {@code SERVICE_V1} projects a {@code ServicePrincipal} (stable audit identity behind a rotatable
 * {@code client_id}); {@code USER_NEUTRAL_V1} projects a foundation {@code HumanAccount}. Both carry
 * {@code token_profile}, {@code claim_contract_version} and the principal's live {@code sec_epoch}, and
 * fail closed when the backing principal cannot be resolved or is not active. There is no unprofiled or
 * compatibility token path after the destructive cutover.
 */
public class AinerJwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final AinerAuthorizationServerProperties properties;
    private final AinerUserDetailsService userDetailsService;
    private final ServicePrincipalFoundationService servicePrincipalFoundationService;
    private final HumanAccountRepository humanAccountRepository;

    public AinerJwtTokenCustomizer(
            AinerAuthorizationServerProperties properties,
            AinerUserDetailsService userDetailsService,
            ServicePrincipalFoundationService servicePrincipalFoundationService,
            HumanAccountRepository humanAccountRepository) {
        this.properties = properties;
        this.userDetailsService = userDetailsService;
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
            throw failedClosed("Ainer access token requires a token profile");
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
        HumanAccount account = humanAccountRepository.findByAccountId(user.accountId())
                .orElseThrow(() -> failedClosed(
                        "HumanAccount not found for USER_NEUTRAL_V1 token: " + user.accountId()));
        if (!account.status().canAuthenticate()) {
            throw failedClosed("HumanAccount " + account.accountId() + " is not active");
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

    private AinerUserDetails ainerUserDetails(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AinerUserDetails user) {
            return user;
        }
        // Passkey 用户经 WebAuthn 认证后，主体是协议 PublicKeyCredentialUserEntity（只含 username），
        // 需经 foundation 解析为 AinerUserDetails 才能投影稳定 account sub。
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
