package dev.ainer.authorizationserver.config;

import dev.ainer.module.identity.account.application.IdentityTokenStatusService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

final class RevocationAwareOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final String ACTOR_TYPE_CLAIM = "actor_type";
    private static final String USER_ACTOR_TYPE = "USER";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String TENANT_CLAIM = "tenant_id";

    private final OAuth2AuthorizationService delegate;
    private final IdentityTokenStatusService identityTokenStatusService;
    private final Predicate<String> registeredClientActive;

    RevocationAwareOAuth2AuthorizationService(
            OAuth2AuthorizationService delegate,
            IdentityTokenStatusService identityTokenStatusService,
            Predicate<String> registeredClientActive) {
        this.delegate = delegate;
        this.identityTokenStatusService = identityTokenStatusService;
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
        if (!USER_ACTOR_TYPE.equals(claims.get(ACTOR_TYPE_CLAIM))) {
            return authorization;
        }

        Instant issuedAt = authorization.getAccessToken().getToken().getIssuedAt();
        boolean active = false;
        try {
            UUID tenantId = UUID.fromString(String.valueOf(claims.get(TENANT_CLAIM)));
            UUID subjectId = UUID.fromString(String.valueOf(claims.get(SUBJECT_CLAIM)));
            if (issuedAt != null) {
                active = identityTokenStatusService.isAccessTokenActive(tenantId, subjectId, issuedAt);
            }
        } catch (IllegalArgumentException exception) {
            active = false;
        }
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
}
