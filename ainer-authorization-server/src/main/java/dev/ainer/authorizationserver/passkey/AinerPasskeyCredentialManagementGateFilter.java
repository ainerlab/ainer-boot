package dev.ainer.authorizationserver.passkey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 凭证管理端点（{@code /webauthn/register/**}）的条件 MFA 门禁。
 *
 * <p>Spring Security 的 WebAuthn 协议 filter 在授权 filter 之前短路处理请求，
 * 因此 {@code authorizeHttpRequests} 上配置的 {@code access(authorizationManager)} 不会对
 * {@code /webauthn/register/**} 生效。本 filter 在协议 filter 之前显式运行同一个
 * {@link AuthorizationManager}：已登记账号必须持有 {@code FACTOR_WEBAUTHN}，否则重定向到登录入口
 * 完成因子，与 {@code /oauth2/authorize} 的条件门禁语义一致。未登记账号的 bootstrap 不受影响。
 */
public final class AinerPasskeyCredentialManagementGateFilter extends OncePerRequestFilter {

    private final AuthorizationManager<RequestAuthorizationContext> authorizationManager;

    public AinerPasskeyCredentialManagementGateFilter(
            AuthorizationManager<RequestAuthorizationContext> authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isCredentialManagement(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            AuthorizationResult decision = authorizationManager.authorize(
                    () -> authentication, new RequestAuthorizationContext(request));
            if (decision == null || !decision.isGranted()) {
                response.sendRedirect("/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isCredentialManagement(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/webauthn/register".equals(path)
                || path.startsWith("/webauthn/register/")
                || "/passkey/recovery-codes".equals(path);
    }
}

