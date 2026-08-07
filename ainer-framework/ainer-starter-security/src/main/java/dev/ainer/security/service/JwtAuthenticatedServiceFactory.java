package dev.ainer.security.service;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;
import java.util.stream.Collectors;

public final class JwtAuthenticatedServiceFactory {

    private JwtAuthenticatedServiceFactory() {
    }

    public static AuthenticatedService from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        if (!AuthenticatedService.SERVICE_ACTOR_TYPE.equals(
                jwt.getClaimAsString(AuthenticatedService.ACTOR_TYPE_CLAIM))) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        try {
            return new AuthenticatedService(jwt.getSubject(), authorities);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
