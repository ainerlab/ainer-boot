package dev.ainer.authorizationserver.token;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CurrentAccessTokenRevocationService {

    private final OAuth2AuthorizationService authorizationService;

    public CurrentAccessTokenRevocationService(OAuth2AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Transactional
    public void revoke(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        OAuth2Authorization authorization =
                authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null
                || authorization.getAccessToken() == null
                || !Objects.equals(
                        tokenValue,
                        authorization.getAccessToken().getToken().getTokenValue())
                || !authorization.getAccessToken().isActive()) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }

        authorizationService.save(OAuth2Authorization.from(authorization)
                .invalidate(authorization.getAccessToken().getToken())
                .build());
    }
}
