package dev.ainer.security.token;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.PrincipalSubjectRef;
import dev.ainer.security.principal.ServiceSubjectRef;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * {@link TokenProfileResolver} 的参考实现（ADR-0033 Greenfield §6.1，S1.1 移植）。
 *
 * <p>使用封闭的 Greenfield profile 把已验证 JWT claim Map 投影为类型化的
 * {@link AuthenticatedPrincipal}。它是失败关闭（fail-closed）的：未知的
 * {@code token_profile}、不支持的 {@code claim_contract_version}、与 profile 不一致的
 * {@code actor_type} 都会抛异常，因此解析器绝不会静默放行未标注 profile 或携带 tenant
 * 语义的 token。该参考实现用于契约验证；授权服务器提供签发/请求期接线，
 * 向它输入真实 claims。
 */
public class ReferenceTokenProfileResolver implements TokenProfileResolver {

    private static final String ACTOR_TYPE_CLAIM = "actor_type";
    private static final String SCOPE_CLAIM = "scope";
    private static final String AMR_CLAIM = "amr";
    private static final String CLIENT_ID_CLAIM = "client_id";
    private static final String SECURITY_EPOCH_CLAIM = "sec_epoch";

    private static final String USER_ACTOR = "USER";
    private static final String SERVICE_ACTOR = "SERVICE";

    @Override
    public AuthenticatedPrincipal resolve(VerifiedJwtClaims claims) {
        Objects.requireNonNull(claims, "claims");
        TokenProfile profile = TokenProfile.fromClaim(stringClaim(claims, TokenProfile.PROFILE_CLAIM));
        String contractVersion = stringClaim(claims, TokenProfile.CONTRACT_VERSION_CLAIM);
        if (!TokenProfile.CURRENT_CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported claim_contract_version: " + contractVersion);
        }
        String actorType = stringClaim(claims, ACTOR_TYPE_CLAIM);

        IdentityAuthorityRef authority = new IdentityAuthorityRef(claims.issuer());
        PrincipalSubjectRef principal = buildPrincipal(profile, authority, claims.subject(), actorType);

        Set<String> scopes = splitScopes(claims.claim(SCOPE_CLAIM));
        String assurance = optionalString(claims, AMR_CLAIM);
        if (assurance == null && profile == TokenProfile.SERVICE_V1) {
            assurance = "client_credentials";
        }
        if (assurance == null) {
            throw new IllegalArgumentException("Missing or blank claim: " + AMR_CLAIM);
        }
        return new AuthenticatedPrincipal(
                principal,
                authority,
                profile,
                contractVersion,
                claims.audiences(),
                scopes,
                assurance,
                optionalString(claims, CLIENT_ID_CLAIM),
                optionalSecurityEpoch(claims));
    }

    private static PrincipalSubjectRef buildPrincipal(
            TokenProfile profile, IdentityAuthorityRef authority, String subject, String actorType) {
        boolean userProfile = profile == TokenProfile.USER_NEUTRAL_V1
                || profile == TokenProfile.USER_WORKSPACE_V1;
        String expectedActor = userProfile ? USER_ACTOR : SERVICE_ACTOR;
        if (!expectedActor.equals(actorType)) {
            throw new IllegalArgumentException(
                    "actor_type " + actorType + " is inconsistent with profile " + profile);
        }
        return userProfile
                ? new HumanSubjectRef(authority, subject)
                : new ServiceSubjectRef(authority, subject);
    }

    private static Set<String> splitScopes(Object scopeClaim) {
        if (scopeClaim == null) {
            return Set.of();
        }
        if (!(scopeClaim instanceof String raw) || raw.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(raw.trim().split("\\s+")));
    }

    private static String stringClaim(VerifiedJwtClaims claims, String name) {
        Object value = claims.claim(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing or blank claim: " + name);
        }
        return text;
    }

    private static String optionalString(VerifiedJwtClaims claims, String name) {
        Object value = claims.claim(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Long optionalSecurityEpoch(VerifiedJwtClaims claims) {
        Object value = claims.claim(SECURITY_EPOCH_CLAIM);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid sec_epoch claim");
        }
        long epoch = number.longValue();
        if (epoch < 0 || Double.compare(number.doubleValue(), epoch) != 0) {
            throw new IllegalArgumentException("Invalid sec_epoch claim");
        }
        return epoch;
    }
}
