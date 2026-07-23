package dev.ainer.security.authorization;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Requires a platform service token that is deliberately not bound to a business tenant.
 */
public final class TenantlessServiceScopeAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ACTOR_TYPE_CLAIM = "actor_type";
    private static final String SERVICE_ACTOR = "SERVICE";
    private static final String TENANT_CLAIM = "tenant_id";

    private final String authority;

    public TenantlessServiceScopeAuthorizationManager(String scope) {
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
                && !token.getToken().getClaims().containsKey(TENANT_CLAIM)
                && token.getAuthorities().stream()
                        .anyMatch(candidate -> authority.equals(candidate.getAuthority()));
        return new AuthorizationDecision(granted);
    }
}
