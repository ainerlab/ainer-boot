package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;

/**
 * 从已解析的 handler 方法上读取 {@link AinerAuthorize} / {@link EndpointAccess} 声明，并在控制器
 * 调用前执行 {@link AinerRequestAuthorizationManager}（ADR-0037 §4）。
 *
 * <p>handler 注解只有在 Spring MVC 解析出 handler 之后才可见，而 servlet 安全过滤链
 * 运行得更早。因此该拦截器自行调用标准 Spring Security {@code AuthorizationManager}，
 * 而不是依赖更早的 {@code AuthorizationFilter} 能看到请求属性。被拒绝的结果会转换为
 * Ainer 的通用禁止传输契约，不暴露决策 id 或 reason code。
 *
 * <p><strong>默认拒绝</strong>：{@code @AinerAuthorize} 是逐方法可选的，没有它的 handler 会落到
 * Resource Server 的 {@code anyRequest().authenticated()}，即「只要求登录、不要求任何权限」。
 * 为避免新端点因为漏写注解而静默对全部已认证主体开放，拦截器对 handler 分四类处理：
 * <ol>
 *   <li>有 {@code @AinerAuthorize} → 走决策引擎（原有路径）；</li>
 *   <li>有 {@code @EndpointAccess} → 按声明的口径放行（{@code PUBLIC} 匿名；{@code AUTHENTICATED}
 *       与 {@code DELEGATED} 仍要求已认证主体）；</li>
 *   <li>第三方 jar 提供的 handler（Spring Boot 错误分发、Actuator、springdoc 文档端点）→ 由外层
 *       Resource Server 链负责认证，拦截器不判权限（包前缀可配，见
 *       {@link EndpointAuthorizationProperties}）；</li>
 *   <li>其余（宿主自己的 Controller 但没写声明）→ 按
 *       {@link EndpointAuthorizationMode} 处置：{@code FAIL_CLOSED}（默认）直接 403 并记 ERROR 日志，
 *       {@code WARN} 记 WARN 日志后放行（沿用旧行为，供升级期灰度）。</li>
 * </ol>
 */
public final class AinerAuthorizeInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AinerAuthorizeInterceptor.class);

    /** 已解析权限 code 的请求属性键。 */
    public static final String PERMISSION_ATTRIBUTE = "ainer.authorization.permission";

    /** 已解析访问模式的请求属性键。 */
    public static final String ACCESS_MODE_ATTRIBUTE = "ainer.authorization.accessMode";

    /**
     * CHALLENGE 结果对应的 RFC 9470 挑战响应头。错误值使用 OAuth/Bearer 既有
     * {@code insufficient_user_authentication} 语义（RFC 9470 §4.1）；协议产物保持 ASCII，
     * 中文说明经由响应体的 Ainer 错误信封传达。
     */
    public static final String WWW_AUTHENTICATE_CHALLENGE =
            "Bearer error=\"insufficient_user_authentication\", "
                    + "error_description=\"recent strong authentication required\"";

    private final AinerRequestAuthorizationManager authorizationManager;
    private final EndpointAuthorizationMode undeclaredMode;
    private final List<String> frameworkHandlerPackages;

    /**
     * 以默认配置构造：未声明端点 {@code FAIL_CLOSED}、第三方 handler 用默认包前缀。
     * 宿主装配路径请使用 {@link #AinerAuthorizeInterceptor(AinerRequestAuthorizationManager,
     * EndpointAuthorizationMode, List)}，让 {@code ainer.security.endpoint-authorization.*} 生效。
     */
    public AinerAuthorizeInterceptor(AinerRequestAuthorizationManager authorizationManager) {
        this(authorizationManager,
                EndpointAuthorizationProperties.DEFAULT_MODE,
                EndpointAuthorizationProperties.DEFAULT_FRAMEWORK_HANDLER_PACKAGES);
    }

    public AinerAuthorizeInterceptor(
            AinerRequestAuthorizationManager authorizationManager,
            EndpointAuthorizationMode undeclaredMode,
            List<String> frameworkHandlerPackages) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager");
        this.undeclaredMode = undeclaredMode != null
                ? undeclaredMode
                : EndpointAuthorizationProperties.DEFAULT_MODE;
        this.frameworkHandlerPackages = frameworkHandlerPackages != null
                ? List.copyOf(frameworkHandlerPackages)
                : EndpointAuthorizationProperties.DEFAULT_FRAMEWORK_HANDLER_PACKAGES;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AinerAuthorize annotation = handlerMethod.getMethodAnnotation(AinerAuthorize.class);
        if (annotation != null) {
            return authorizeAnnotatedHandler(request, response, annotation);
        }
        EndpointAccess declaration = resolveDeclaration(handlerMethod);
        if (declaration != null) {
            return enforceDeclaration(handlerMethod, declaration);
        }
        if (isFrameworkProvidedHandler(handlerMethod)) {
            return true;
        }
        return handleUndeclaredHandler(request, handlerMethod);
    }

    /**
     * 解析 handler 的访问声明：方法级优先，其次类级（{@link EndpointAccess} 支持
     * {@code @Target(TYPE)}；{@link AinerAuthorize} 只支持方法级）。
     */
    public static EndpointAccess resolveDeclaration(HandlerMethod handlerMethod) {
        EndpointAccess methodLevel = handlerMethod.getMethodAnnotation(EndpointAccess.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), EndpointAccess.class);
    }

    private boolean authorizeAnnotatedHandler(
            HttpServletRequest request, HttpServletResponse response, AinerAuthorize annotation) {
        request.setAttribute(PERMISSION_ATTRIBUTE, annotation.permission());
        request.setAttribute(ACCESS_MODE_ATTRIBUTE, annotation.accessMode());
        AuthorizationResult result = authorizationManager.authorize(
                () -> SecurityContextHolder.getContext().getAuthentication(),
                new RequestAuthorizationContext(request));
        if (result != null && result instanceof AinerAuthorizationResult ainerResult
                && ainerResult.decision().outcome()
                        == dev.ainer.authorization.domain.AuthorizationOutcome.CHALLENGE) {
            // 高风险权限且缺少近期强认证：调用方必须重新认证（401），
            // 而不是被告知操作被禁止（403）。按 RFC 9470 附带标准挑战头，
            // 客户端据此回到 Authorization Server 完成 step-up 后重试。
            response.setHeader("WWW-Authenticate", WWW_AUTHENTICATE_CHALLENGE);
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED,
                    "该操作需要近期强认证后重试");
        }
        if (result == null || !result.isGranted()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return true;
    }

    private boolean enforceDeclaration(HandlerMethod handlerMethod, EndpointAccess declaration) {
        if (declaration.reason().isBlank()) {
            LOGGER.warn("@EndpointAccess 没有写 reason，审计时无法追问放行依据：{}#{}",
                    handlerMethod.getBeanType().getName(), handlerMethod.getMethod().getName());
        }
        if (declaration.kind() == EndpointAccess.Kind.PUBLIC) {
            // 匿名端点：可达性由外层 Resource Server 的 public-paths 决定（filter chain 先执行）。
            // 只写声明不写 public-paths 时匿名请求仍是 401——失败关闭方向，符合预期。
            return true;
        }
        if (!hasAuthenticatedPrincipal()) {
            // AUTHENTICATED / DELEGATED 都不接受匿名主体。外层链通常已返回 401；
            // 这里兜住「端点被误配进 public-paths」的情形，不让声明被绕过。
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED, "该端点要求已认证主体");
        }
        return true;
    }

    /**
     * 第三方 jar 提供的 MVC handler 不是宿主的 Controller（拿不到注解、也无法逐个审阅），
     * 其认证由外层 Resource Server 链负责；这里按包前缀放行，避免把 {@code /error}、
     * Actuator、springdoc 文档端点一并拒掉。
     */
    private boolean isFrameworkProvidedHandler(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();
        for (String prefix : frameworkHandlerPackages) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleUndeclaredHandler(HttpServletRequest request, HandlerMethod handlerMethod) {
        String endpoint = handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
        if (undeclaredMode == EndpointAuthorizationMode.WARN) {
            LOGGER.warn("端点未声明授权口径，WARN 模式放行（只要求已认证）：{} {} -> {}；"
                            + "请补 @AinerAuthorize 或 @EndpointAccess，并把 "
                            + "ainer.security.endpoint-authorization.mode 设为 fail-closed",
                    request.getMethod(), request.getRequestURI(), endpoint);
            return true;
        }
        LOGGER.error("端点未声明授权口径，FAIL_CLOSED 拒绝：{} {} -> {}；"
                        + "请补 @AinerAuthorize(permission=...) 或 @EndpointAccess(kind=..., reason=...)",
                request.getMethod(), request.getRequestURI(), endpoint);
        throw new BusinessException(StandardErrorCode.FORBIDDEN);
    }

    private static boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
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
