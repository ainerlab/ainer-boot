package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * Reads the {@link AinerAuthorize} annotation from the resolved handler method and executes the
 * {@link AinerRequestAuthorizationManager} before controller invocation (ADR-0037 §4).
 *
 * <p>Handler annotations are only available after Spring MVC resolves the handler, while the servlet
 * security filter chain runs earlier. The interceptor therefore invokes the standard Spring Security
 * {@code AuthorizationManager} itself instead of relying on request attributes being visible to an
 * earlier {@code AuthorizationFilter}. A denied result is translated to Ainer's generic forbidden
 * transport contract without exposing the decision id or reason code.
 */
public final class AinerAuthorizeInterceptor implements HandlerInterceptor {

    /** Request attribute key for the resolved permission code. */
    public static final String PERMISSION_ATTRIBUTE = "ainer.authorization.permission";

    /** Request attribute key for the resolved access mode. */
    public static final String ACCESS_MODE_ATTRIBUTE = "ainer.authorization.accessMode";

    private final AinerRequestAuthorizationManager authorizationManager;

    public AinerAuthorizeInterceptor(AinerRequestAuthorizationManager authorizationManager) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            AinerAuthorize annotation = handlerMethod.getMethodAnnotation(AinerAuthorize.class);
            if (annotation != null) {
                request.setAttribute(PERMISSION_ATTRIBUTE, annotation.permission());
                request.setAttribute(ACCESS_MODE_ATTRIBUTE, annotation.accessMode());
                AuthorizationResult result = authorizationManager.authorize(
                        () -> SecurityContextHolder.getContext().getAuthentication(),
                        new RequestAuthorizationContext(request));
                if (result != null && result instanceof AinerAuthorizationResult ainerResult
                        && ainerResult.decision().outcome()
                                == dev.ainer.authorization.domain.AuthorizationOutcome.CHALLENGE) {
                    // HIGH-risk permission without recent strong authentication: the caller must
                    // re-authenticate (401), not be told the action is forbidden (403).
                    throw new BusinessException(StandardErrorCode.UNAUTHENTICATED,
                            "该操作需要近期强认证后重试");
                }
                if (result == null || !result.isGranted()) {
                    throw new BusinessException(StandardErrorCode.FORBIDDEN);
                }
            }
        }
        return true;
    }

    /**
     * Convenience accessor for the permission attribute, used by the authorization manager or
     * application services.
     */
    public static String resolvePermission(HttpServletRequest request) {
        Object value = request.getAttribute(PERMISSION_ATTRIBUTE);
        return value instanceof String s ? s : null;
    }

    /**
     * Convenience accessor for the access mode attribute; defaults to {@link AccessMode#AUTHENTICATED}.
     */
    public static AccessMode resolveAccessMode(HttpServletRequest request) {
        Object value = request.getAttribute(ACCESS_MODE_ATTRIBUTE);
        return value instanceof AccessMode am ? am : AccessMode.AUTHENTICATED;
    }
}
