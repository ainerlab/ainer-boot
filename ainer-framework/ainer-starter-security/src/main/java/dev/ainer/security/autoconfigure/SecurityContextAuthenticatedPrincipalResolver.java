package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
import dev.ainer.security.token.TokenProfileResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

final class SecurityContextAuthenticatedPrincipalResolver implements AuthenticatedPrincipalResolver {

    private final TokenProfileResolver tokenProfileResolver;

    SecurityContextAuthenticatedPrincipalResolver(TokenProfileResolver tokenProfileResolver) {
        this.tokenProfileResolver = Objects.requireNonNull(tokenProfileResolver, "tokenProfileResolver");
    }

    @Override
    public AuthenticatedPrincipal requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        Object profile = jwt.getClaim(TokenProfile.PROFILE_CLAIM);
        if (!(profile instanceof String value) || value.isBlank()) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        try {
            return tokenProfileResolver.resolve(JwtToVerifiedJwtClaims.from(jwt));
        } catch (RuntimeException exception) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
    }
}
