package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AccessMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads the {@link AinerAuthorize} annotation from the handler method and sets the permission and
 * access mode as request attributes (ADR-0037 §4). The {@link AinerRequestAuthorizationManager}
 * reads these attributes when evaluating the request.
 *
 * <p>This interceptor must run <em>before</em> the authorization check. In practice it is registered
 * as a Spring MVC {@code InterceptorRegistry} add, and the {@code AuthorizationManager} is wired into
 * the {@code SecurityFilterChain} — the filter chain runs before Spring MVC dispatch, so this
 * interceptor populates attributes that are read by a subsequent adapter call, or by the application
 * service that calls {@link dev.ainer.authorization.AuthorizationService} directly.
 */
public class AinerAuthorizeInterceptor implements HandlerInterceptor {

    /** Request attribute key for the resolved permission code. */
    public static final String PERMISSION_ATTRIBUTE = "ainer.authorization.permission";

    /** Request attribute key for the resolved access mode. */
    public static final String ACCESS_MODE_ATTRIBUTE = "ainer.authorization.accessMode";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            AinerAuthorize annotation = handlerMethod.getMethodAnnotation(AinerAuthorize.class);
            if (annotation != null) {
                request.setAttribute(PERMISSION_ATTRIBUTE, annotation.permission());
                request.setAttribute(ACCESS_MODE_ATTRIBUTE, annotation.accessMode());
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
