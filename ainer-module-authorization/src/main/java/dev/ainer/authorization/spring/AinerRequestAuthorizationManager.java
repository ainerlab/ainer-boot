package dev.ainer.authorization.spring;

import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HTTP-layer Spring Security {@link AuthorizationManager} backed by Ainer's
 * {@link AuthorizationService} (ADR-0037 §4, ADR-0030 §8.2).
 *
 * <p>For each request matched to this manager, it:
 * <ol>
 *   <li>resolves the {@link AuthenticatedPrincipal} via {@link AuthenticatedPrincipalResolver}
 *       (which reads the verified JWT from the SecurityContext);</li>
 *   <li>reads the permission code and access mode from request attributes set by
 *       {@link AinerAuthorizeInterceptor} (or defaults to authenticated);</li>
 *   <li>builds an {@link AuthorizationRequest} and calls {@link AuthorizationService#authorize};</li>
 *   <li>maps the {@link AuthorizationDecision} to an {@link AinerAuthorizationResult}.</li>
 * </ol>
 *
 * <p><strong>First-version limitation</strong>: {@link ResourceRef} is a generic placeholder
 * (workspaceId from request attribute if present, otherwise a synthetic "any" resource). A typed
 * {@code AuthorizationTargetResolver} that maps path variables / request body to concrete
 * {@code ResourceRef} is a future slice (ADR-0037 §4). Until then, this manager is suitable for
 * coarse-grained permission gates (e.g. "must have {@code authorization.manage}"), not for
 * per-resource ownership checks — those must still be done explicitly in the application service
 * (ADR-0030 §8.4).
 *
 * <p>This class lives in the {@code spring/} adapter boundary (ADR-0037 §3).
 */
public final class AinerRequestAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private final AuthorizationService authorizationService;
    private final AuthenticatedPrincipalResolver principalResolver;

    public AinerRequestAuthorizationManager(
            AuthorizationService authorizationService,
            AuthenticatedPrincipalResolver principalResolver) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.principalResolver = Objects.requireNonNull(principalResolver, "principalResolver");
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String permissionCode = AinerAuthorizeInterceptor.resolvePermission(request);
        if (permissionCode == null || permissionCode.isBlank()) {
            // No @AinerAuthorize on this handler — defer to the default anyRequest().authenticated() policy.
            // Returning null tells Spring Security to fall through to the next manager / matcher.
            return null;
        }

        AuthenticatedPrincipal principal;
        try {
            principal = principalResolver.requireCurrent();
        } catch (RuntimeException unresolved) {
            // Unauthenticated or invalid token — deny (SecurityFilterChain's 401 entry point handles
            // the missing-authentication case; if we get here it's a FORBIDDEN).
            return new AinerAuthorizationResult(AuthorizationDecision.deny(
                    dev.ainer.authorization.AuthorizationReasonCodes.AUTHENTICATED_REQUIRED,
                    "ainer-adapter", Instant.now()));
        }

        AccessMode accessMode = AinerAuthorizeInterceptor.resolveAccessMode(request);
        Requester requester = toRequester(principal);
        ResourceRef resource = resolveResource(request);
        AuthorizationRequest authRequest = new AuthorizationRequest(
                requester,
                accessMode,
                new PermissionCode(permissionCode),
                resource,
                new AuthorizationContext(
                        Instant.now(),
                        AuthorizationContext.Assurance.NONE,
                        null,
                        null,
                        null));

        AuthorizationDecision decision = authorizationService.authorize(authRequest);
        return new AinerAuthorizationResult(decision);
    }

    private static Requester toRequester(AuthenticatedPrincipal principal) {
        return new Requester.Authenticated(
                new dev.ainer.authorization.domain.SubjectRef(
                        principal.authority().issuer(),
                        principal.subjectId(),
                        principal.isService()
                                ? dev.ainer.authorization.domain.SubjectType.SERVICE
                                : dev.ainer.authorization.domain.SubjectType.USER),
                principal.scopes(),
                principal.audiences(),
                principal.clientId());
    }

    /**
     * Resolve a {@link ResourceRef} from the request. First version uses a synthetic "any-resource"
     * placeholder with an optional workspaceId from a request attribute
     * ({@code ainer.authorization.workspaceId}). A typed target resolver is a future slice.
     */
    private static ResourceRef resolveResource(HttpServletRequest request) {
        Object wsId = request.getAttribute("ainer.authorization.workspaceId");
        UUID workspaceId = wsId instanceof UUID u ? u : null;
        return new ResourceRef(workspaceId, new ResourceType("request"), UUID.nameUUIDFromBytes(
                request.getRequestURI().getBytes()));
    }
}
