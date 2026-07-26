package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;
import java.util.stream.Collectors;

final class SecurityContextAuthenticatedActorResolver implements AuthenticatedActorResolver {

    private final AinerResourceServerProperties properties;

    SecurityContextAuthenticatedActorResolver(AinerResourceServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public AuthenticatedActor requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }

        String subjectId = jwt.getClaimAsString(properties.getSubjectClaim());
        String tenantId = jwt.getClaimAsString(properties.getTenantClaim());
        String actorType = jwt.getClaimAsString("actor_type");
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        try {
            return new AuthenticatedActor(subjectId, tenantId, actorType, authorities);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
