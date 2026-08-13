package dev.ainer.authorizationserver.config;

import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import dev.ainer.security.token.TokenProfile;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

final class RevocationAwareOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final String ACTOR_TYPE_CLAIM = "actor_type";
    private static final String USER_ACTOR_TYPE = "USER";
    private static final String SERVICE_ACTOR_TYPE = "SERVICE";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String SECURITY_EPOCH_CLAIM = "sec_epoch";

    private final OAuth2AuthorizationService delegate;
    private final HumanAccountRepository humanAccountRepository;
    private final ServicePrincipalRepository servicePrincipalRepository;
    private final Predicate<String> registeredClientActive;

    RevocationAwareOAuth2AuthorizationService(
            OAuth2AuthorizationService delegate,
            HumanAccountRepository humanAccountRepository,
            ServicePrincipalRepository servicePrincipalRepository,
            Predicate<String> registeredClientActive) {
        this.delegate = delegate;
        this.humanAccountRepository = humanAccountRepository;
        this.servicePrincipalRepository = servicePrincipalRepository;
        this.registeredClientActive = registeredClientActive;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        if (authorization != null
                && !registeredClientActive.test(authorization.getRegisteredClientId())) {
            return null;
        }
        return applyIdentityStatus(authorization);
    }

    private OAuth2Authorization applyIdentityStatus(OAuth2Authorization authorization) {
        if (authorization == null || authorization.getAccessToken() == null) {
            return authorization;
        }
        Map<String, Object> claims = authorization.getAccessToken().getClaims();
        if (!isProfiledPrincipal(claims)) {
            return authorization;
        }

        boolean active = isCurrentEpoch(claims);
        if (active) {
            return authorization;
        }

        OAuth2Authorization.Builder invalidated = OAuth2Authorization.from(authorization)
                .invalidate(authorization.getAccessToken().getToken());
        if (authorization.getRefreshToken() != null) {
            invalidated.invalidate(authorization.getRefreshToken().getToken());
        }
        return invalidated.build();
    }

    private boolean isProfiledPrincipal(Map<String, Object> claims) {
        Object profile = claims.get(TokenProfile.PROFILE_CLAIM);
        Object contract = claims.get(TokenProfile.CONTRACT_VERSION_CLAIM);
        Object actor = claims.get(ACTOR_TYPE_CLAIM);
        return TokenProfile.USER_NEUTRAL_V1.claimValue().equals(profile)
                && TokenProfile.CURRENT_CONTRACT_VERSION.equals(contract)
                && USER_ACTOR_TYPE.equals(actor)
                || TokenProfile.SERVICE_V1.claimValue().equals(profile)
                && TokenProfile.CURRENT_CONTRACT_VERSION.equals(contract)
                && SERVICE_ACTOR_TYPE.equals(actor);
    }

    private boolean isCurrentEpoch(Map<String, Object> claims) {
        Object subject = claims.get(SUBJECT_CLAIM);
        Object epoch = claims.get(SECURITY_EPOCH_CLAIM);
        if (!(subject instanceof String subjectValue) || subjectValue.isBlank()
                || !(epoch instanceof Number number)) {
            return false;
        }
        long tokenEpoch = number.longValue();
        if (tokenEpoch < 0 || Double.compare(number.doubleValue(), tokenEpoch) != 0) {
            return false;
        }
        try {
            UUID principalId = UUID.fromString(subjectValue);
            if (USER_ACTOR_TYPE.equals(claims.get(ACTOR_TYPE_CLAIM))) {
                return humanAccountRepository.findByAccountId(principalId)
                        .filter(account -> account.status().canAuthenticate())
                        .map(account -> account.securityEpoch() == tokenEpoch)
                        .orElse(false);
            }
            return servicePrincipalRepository.findByPrincipalId(principalId)
                    .filter(principal -> principal.status().canAuthenticate())
                    .map(principal -> principal.securityEpoch() == tokenEpoch)
                    .orElse(false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
