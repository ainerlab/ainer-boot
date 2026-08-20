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
 * 从已解析的 handler 方法上读取 {@link AinerAuthorize} 注解，并在控制器调用前执行
 * {@link AinerRequestAuthorizationManager}（ADR-0037 §4）。
 *
 * <p>handler 注解只有在 Spring MVC 解析出 handler 之后才可见，而 servlet 安全过滤链
 * 运行得更早。因此该拦截器自行调用标准 Spring Security {@code AuthorizationManager}，
 * 而不是依赖更早的 {@code AuthorizationFilter} 能看到请求属性。被拒绝的结果会转换为
 * Ainer 的通用禁止传输契约，不暴露决策 id 或 reason code。
 */
public final class AinerAuthorizeInterceptor implements HandlerInterceptor {

    /** 已解析权限 code 的请求属性键。 */
    public static final String PERMISSION_ATTRIBUTE = "ainer.authorization.permission";

    /** 已解析访问模式的请求属性键。 */
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
                    // 高风险权限且缺少近期强认证：调用方必须重新认证（401），
                    // 而不是被告知操作被禁止（403）。
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
     * 权限属性的便捷读取方法，供授权管理器或应用服务使用。
     */
    public static String resolvePermission(HttpServletRequest request) {
        Object value = request.getAttribute(PERMISSION_ATTRIBUTE);
        return value instanceof String s ? s : null;
    }

    /**
     * 访问模式属性的便捷读取方法；默认 {@link AccessMode#AUTHENTICATED}。
     */
    public static AccessMode resolveAccessMode(HttpServletRequest request) {
        Object value = request.getAttribute(ACCESS_MODE_ATTRIBUTE);
        return value instanceof AccessMode am ? am : AccessMode.AUTHENTICATED;
    }
}
