package dev.ainer.security.authorization;

import dev.ainer.security.token.TokenProfile;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/** Requires a typed SERVICE token with the requested scope. */
public final class ServiceScopeAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ACTOR_TYPE_CLAIM = "actor_type";
    private static final String SERVICE_ACTOR = "SERVICE";

    private final String authority;

    public ServiceScopeAuthorizationManager(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        this.authority = "SCOPE_" + scope;
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        boolean granted = current instanceof JwtAuthenticationToken token
                && current.isAuthenticated()
                && token.getToken().getSubject() != null
                && !token.getToken().getSubject().isBlank()
                && SERVICE_ACTOR.equals(token.getToken().getClaimAsString(ACTOR_TYPE_CLAIM))
                && TokenProfile.SERVICE_V1.claimValue().equals(
                        token.getToken().getClaimAsString(TokenProfile.PROFILE_CLAIM))
                && TokenProfile.CURRENT_CONTRACT_VERSION.equals(
                        token.getToken().getClaimAsString(TokenProfile.CONTRACT_VERSION_CLAIM))
                && token.getAuthorities().stream()
                        .anyMatch(candidate -> authority.equals(candidate.getAuthority()));
        return new AuthorizationDecision(granted);
    }
}
