package dev.ainer.authorizationserver.tenantcontext;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.TenantContextEntry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 拦截 authorization endpoint（GET /oauth2/authorize），对持有多个 ACTIVE membership 的人员
 * 强制租户上下文选择。见 ADR-0019 decision 17。
 *
 * <p>锚定在 {@code @Order(1)} authorization server 链中 {@code SecurityContextHolderFilter}
 * 之后、Spring AS authorization endpoint filter 之前：
 * <ul>
 *   <li>只有一个 ACTIVE membership（或零个）时直接放行，沿用默认落点；</li>
 *   <li>多个 ACTIVE membership 且会话中尚未记录选择时，保存原始 authorization URL 并重定向到
 *       {@code /select-tenant}；</li>
 *   <li>选择完成后会话记录已选 tenant，后续同一 authorization request 直接放行。</li>
 * </ul>
 * 选择本身不创建、不修改 membership；最终 claim 由 token customizer 实时重查决定。
 */
public final class AinerTenantSelectionFilter extends OncePerRequestFilter {

    static final String RESUMPTION_URL_ATTRIBUTE = "ainer.tenant-selection.resumption-url";
    static final String SELECTED_TENANT_ATTRIBUTE = "ainer.tenant-selection.selected-tenant";
    static final String AVAILABLE_MEMBERSHIPS_ATTRIBUTE = "ainer.tenant-selection.available-memberships";

    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";

    private final IdentityApplicationService identityService;

    public AinerTenantSelectionFilter(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAuthorizationEndpoint(request)) {
            chain.doFilter(request, response);
            return;
        }
        AinerUserDetails user = currentUser();
        if (user == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!user.hasLegacyTenantContext()) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (hasCompletedSelection(session, user)) {
            chain.doFilter(request, response);
            return;
        }
        List<TenantContextEntry> memberships = identityService.findActiveMemberships(user.subjectId());
        if (memberships.size() <= 1) {
            chain.doFilter(request, response);
            return;
        }
        requireSelection(request, response, session, memberships);
    }

    private static boolean isAuthorizationEndpoint(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        return AUTHORIZATION_ENDPOINT.equals(request.getServletPath());
    }

    private static AinerUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AinerUserDetails user ? user : null;
    }

    private static boolean hasCompletedSelection(HttpSession session, AinerUserDetails user) {
        if (session == null) {
            return false;
        }
        Object selected = session.getAttribute(SELECTED_TENANT_ATTRIBUTE);
        if (!(selected instanceof java.util.UUID selectedTenantId)) {
            return false;
        }
        return selectedTenantId.equals(user.tenantId());
    }

    private void requireSelection(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            List<TenantContextEntry> memberships) throws IOException {
        HttpSession currentSession = session != null ? session : request.getSession(true);
        currentSession.setAttribute(RESUMPTION_URL_ATTRIBUTE, request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
        currentSession.setAttribute(AVAILABLE_MEMBERSHIPS_ATTRIBUTE, memberships);
        response.sendRedirect(request.getContextPath() + "/select-tenant");
    }
}
