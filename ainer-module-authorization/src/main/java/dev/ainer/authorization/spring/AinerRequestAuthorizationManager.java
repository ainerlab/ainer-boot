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
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 由 Ainer {@link AuthorizationService} 支撑的 HTTP 层 Spring Security
 * {@link AuthorizationManager}（ADR-0037 §4、ADR-0030 §8.2）。
 *
 * <p>对每个匹配到该管理器的请求，它依次：
 * <ol>
 *   <li>通过 {@link AuthenticatedPrincipalResolver} 解析 {@link AuthenticatedPrincipal}
 *       （从 SecurityContext 读取已验证 JWT）；</li>
 *   <li>从 {@link AinerAuthorizeInterceptor} 写入的请求属性读取权限 code 与访问模式；</li>
 *   <li>构建 {@link AuthorizationRequest} 并调用 {@link AuthorizationService#authorize}；
 *       目标资源优先由已注册的 {@link AuthorizationTargetResolver} bean 链解析（第一个非空结果
 *       胜出，ADR-0037 §4），没有解析器应答时回退到合成 {@code request} 资源占位；</li>
 *   <li>把 {@link AuthorizationDecision} 映射为 {@link AinerAuthorizationResult}。</li>
 * </ol>
 *
 * <p>该管理器由 {@link AinerAuthorizeInterceptor} 在 Spring MVC 解析出带注解的 handler
 * 之后调用。它有意不作为更早的 servlet 过滤链中的兜底管理器安装，因为那一阶段还不存在
 * handler 注解。
 *
 * <p><strong>目标解析边界</strong>：未注册 {@link AuthorizationTargetResolver} 时，
 * {@link ResourceRef} 是通用占位（若请求属性中存在 workspaceId 则使用之，否则合成一个
 * "任意"资源），适合粗粒度权限闸门（例如"必须持有 {@code authorization.manage}"）。
 * 需要逐资源归属检查的产品应注册类型化解析器提供真实 {@code ResourceRef}；即便如此，
 * 高价值写操作仍必须在应用服务中显式授权（ADR-0030 §8.4）。
 *
 * <p>该类位于 {@code spring/} 适配器边界（ADR-0037 §3）。
 */
public final class AinerRequestAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    /** step-up 过滤器在当前 Token 携带近期强认证时发布的请求属性键。 */
    public static final String RECENT_STRONG_AUTH_ATTRIBUTE =
            "dev.ainer.security.authorization.recentStrongAuthentication";

    /** 可选的 workspace 归属请求属性键（合成占位回退路径仍消费它）。 */
    public static final String WORKSPACE_ID_ATTRIBUTE = "ainer.authorization.workspaceId";

    /**
     * 完整 {@link dev.ainer.authorization.domain.AuthorizationDecision} 的请求属性键。
     * 管理器在每次决策后写入；controller 可据此显式消费决策携带的公开投影描述符等数据，
     * 而不必重新调用授权服务。
     */
    public static final String DECISION_ATTRIBUTE = "ainer.authorization.decision";

    private final AuthorizationService authorizationService;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final ObjectProvider<
            dev.ainer.authorization.application.AuthorizationDecisionAuditService> decisionAudit;
    private final ObjectProvider<AuthorizationTargetResolver> targetResolvers;

    public AinerRequestAuthorizationManager(
            AuthorizationService authorizationService,
            AuthenticatedPrincipalResolver principalResolver,
            ObjectProvider<
                    dev.ainer.authorization.application.AuthorizationDecisionAuditService> decisionAudit) {
        this(authorizationService, principalResolver, decisionAudit, null);
    }

    public AinerRequestAuthorizationManager(
            AuthorizationService authorizationService,
            AuthenticatedPrincipalResolver principalResolver,
            ObjectProvider<
                    dev.ainer.authorization.application.AuthorizationDecisionAuditService> decisionAudit,
            ObjectProvider<AuthorizationTargetResolver> targetResolvers) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.principalResolver = Objects.requireNonNull(principalResolver, "principalResolver");
        this.decisionAudit = decisionAudit;
        this.targetResolvers = targetResolvers;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String permissionCode = AinerAuthorizeInterceptor.resolvePermission(request);
        if (permissionCode == null || permissionCode.isBlank()) {
            // 该 handler 上没有 @AinerAuthorize——交给默认的 anyRequest().authenticated() 策略。
            // 返回 null 告知 Spring Security 落入下一个管理器/匹配器。
            return null;
        }

        AccessMode accessMode = AinerAuthorizeInterceptor.resolveAccessMode(request);
        Requester requester = resolveRequester(accessMode);
        if (requester == null) {
            return new AinerAuthorizationResult(AuthorizationDecision.deny(
                    dev.ainer.authorization.AuthorizationReasonCodes.AUTHENTICATED_REQUIRED,
                    "ainer-adapter", Instant.now()));
        }
        ResourceRef resource = resolveResource(request, permissionCode);
        AuthorizationRequest authRequest = new AuthorizationRequest(
                requester,
                accessMode,
                new PermissionCode(permissionCode),
                resource,
                new AuthorizationContext(
                        Instant.now(),
                        resolveAssurance(request),
                        null,
                        request.getHeader("X-Request-Id"),
                        null));

        AuthorizationDecision decision = authorizationService.authorize(authRequest);
        request.setAttribute(DECISION_ATTRIBUTE, decision);
        recordDecisionAudit(authRequest, decision, request);
        return new AinerAuthorizationResult(decision);
    }

    /**
     * step-up 过滤器把强认证结论发布为请求属性；授权上下文消费它，让 HIGH 风险权限能够
     * 区分"近期强认证"与普通会话，而不是一律发起挑战。
     */
    private static AuthorizationContext.Assurance resolveAssurance(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(RECENT_STRONG_AUTH_ATTRIBUTE))
                ? AuthorizationContext.Assurance.RECENT_STRONG
                : AuthorizationContext.Assurance.NONE;
    }

    /**
     * 按权限的审计级别持久化决策审计行（ADR-0037 §12.4）。服务在独立事务中写入，
     * 因此 DENY 审计在任何后续业务回滚后仍存活；审计失败会传播并阻断请求（fail-closed）。
     */
    private void recordDecisionAudit(
            AuthorizationRequest authRequest, AuthorizationDecision decision,
            HttpServletRequest request) {
        dev.ainer.authorization.application.AuthorizationDecisionAuditService audit =
                decisionAudit.getIfAvailable();
        if (audit != null) {
            audit.recordIfApplicable(
                    authRequest, decision,
                    request.getHeader("X-Request-Id"), null);
        }
    }

    private Requester resolveRequester(AccessMode accessMode) {
        try {
            return toRequester(principalResolver.requireCurrent());
        } catch (BusinessException unresolved) {
            return accessMode == AccessMode.PUBLIC_PROJECTION ? new Requester.Anonymous() : null;
        }
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
     * 从请求解析 {@link ResourceRef}：优先按 bean 顺序询问已注册的
     * {@link AuthorizationTargetResolver}（第一个非空结果胜出，ADR-0037 §4）；解析器抛出的
     * 异常原样传播（故障组件不得静默降级为合成资源放行）。没有任何解析器应答时回退到合成
     * "任意资源"占位，workspaceId 可选地来自请求属性（{@code ainer.authorization.workspaceId}）。
     */
    private ResourceRef resolveResource(HttpServletRequest request, String permissionCode) {
        if (targetResolvers != null) {
            for (AuthorizationTargetResolver resolver : targetResolvers.orderedStream().toList()) {
                Optional<ResourceRef> resolved = resolver.resolve(request, permissionCode);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            }
        }
        Object wsId = request.getAttribute(WORKSPACE_ID_ATTRIBUTE);
        UUID workspaceId = wsId instanceof UUID u ? u : null;
        return new ResourceRef(workspaceId, new ResourceType("request"), UUID.nameUUIDFromBytes(
                request.getRequestURI().getBytes(StandardCharsets.UTF_8)));
    }
}
