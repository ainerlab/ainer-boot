package dev.ainer.authorizationserver.tenantcontext;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.account.application.TenantContextEntry;
import dev.ainer.module.identity.account.domain.TenantRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 租户上下文选择页面。只有 {@link AinerTenantSelectionFilter} 判定需要选择时才会到达。
 *
 * <p>GET 展示当前 ACTIVE membership 列表；POST 校验选择并更新 SecurityContext principal
 * 使后续 authorization request 在选定 tenant 上下文中签发。选择本身不修改 membership，
 * 最终 claim 由 token customizer 实时重查决定。
 */
@Controller
@ConditionalOnProperty(
        prefix = "ainer.identity",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AinerTenantSelectionController {

    private static final String TENANT_ROLE_PREFIX = "ROLE_";

    private final IdentityApplicationService identityService;

    public AinerTenantSelectionController(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/select-tenant")
    public void showPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AinerUserDetails user = currentUser();
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        List<TenantContextEntry> memberships = availableMemberships(request, user);
        if (memberships.size() <= 1) {
            resumeAuthorization(request, response);
            return;
        }
        renderPage(request, response, user, memberships, null);
    }

    @PostMapping("/select-tenant")
    public void processSelection(
            @RequestParam @NotNull UUID tenantId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        AinerUserDetails user = currentUser();
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        IdentityDirectoryEntry membership = identityService.findActiveMembership(tenantId, user.subjectId())
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        switchTenantContext(user, membership);
        HttpSession session = request.getSession(true);
        session.setAttribute(AinerTenantSelectionFilter.SELECTED_TENANT_ATTRIBUTE, tenantId);
        session.removeAttribute(AinerTenantSelectionFilter.AVAILABLE_MEMBERSHIPS_ATTRIBUTE);
        resumeAuthorization(request, response);
    }

    private static AinerUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AinerUserDetails user ? user : null;
    }

    @SuppressWarnings("unchecked")
    private static List<TenantContextEntry> availableMemberships(
            HttpServletRequest request, AinerUserDetails user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object cached = session.getAttribute(AinerTenantSelectionFilter.AVAILABLE_MEMBERSHIPS_ATTRIBUTE);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof TenantContextEntry) {
                return (List<TenantContextEntry>) list;
            }
        }
        return List.of();
    }

    private void switchTenantContext(AinerUserDetails current, IdentityDirectoryEntry membership) {
        TenantRole role = membership.role();
        AinerUserDetails updated = new AinerUserDetails(
                current.subjectId(),
                membership.tenantId(),
                current.getUsername(),
                current.getPassword(),
                current.isEnabled(),
                current.isAccountNonLocked(),
                List.of(new SimpleGrantedAuthority(TENANT_ROLE_PREFIX + role.name())));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken newAuthentication = new UsernamePasswordAuthenticationToken(
                updated, authentication != null ? authentication.getCredentials() : null,
                updated.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuthentication);
    }

    private void resumeAuthorization(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String resumptionUrl = null;
        if (session != null) {
            Object cached = session.getAttribute(AinerTenantSelectionFilter.RESUMPTION_URL_ATTRIBUTE);
            if (cached instanceof String url) {
                resumptionUrl = url;
                session.removeAttribute(AinerTenantSelectionFilter.RESUMPTION_URL_ATTRIBUTE);
            }
        }
        if (resumptionUrl == null || resumptionUrl.isBlank()) {
            resumptionUrl = request.getContextPath() + "/";
        }
        response.sendRedirect(resumptionUrl);
    }

    private void renderPage(
            HttpServletRequest request,
            HttpServletResponse response,
            AinerUserDetails user,
            List<TenantContextEntry> memberships,
            String error) throws IOException {
        CsrfToken csrfToken = csrfToken(request);
        String contextPath = request.getContextPath();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.getWriter().write(pageHtml(contextPath, csrfToken, user, memberships, error));
    }

    private static CsrfToken csrfToken(HttpServletRequest request) {
        Object token = request.getAttribute(CsrfToken.class.getName());
        if (!(token instanceof CsrfToken)) {
            token = request.getAttribute("_csrf");
        }
        if (token instanceof CsrfToken csrfToken) {
            return csrfToken;
        }
        throw new IllegalStateException("Spring Security CSRF token is unavailable");
    }

    private static String pageHtml(
            String contextPath,
            CsrfToken csrfToken,
            AinerUserDetails user,
            List<TenantContextEntry> memberships,
            String error) {
        StringBuilder options = new StringBuilder();
        for (TenantContextEntry membership : memberships) {
            String checked = membership.defaultTenant() ? " checked" : "";
            options.append("""
                    <label class="tenant-option">
                      <input type="radio" name="tenantId" value="%s"%s>
                      <span class="tenant-name">%s</span>
                      <span class="tenant-code">%s</span>
                      <span class="tenant-role">%s%s</span>
                    </label>
                    """.formatted(
                    HtmlUtils.htmlEscape(membership.tenantId().toString()),
                    checked,
                    HtmlUtils.htmlEscape(membership.tenantName()),
                    HtmlUtils.htmlEscape(membership.tenantCode()),
                    HtmlUtils.htmlEscape(membership.role().name()),
                    membership.defaultTenant() ? " · 默认" : ""));
        }
        String errorBlock = error == null || error.isBlank() ? ""
                : "<div class=\"error\" role=\"alert\">" + HtmlUtils.htmlEscape(error) + "</div>";
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex, nofollow">
                  <title>选择租户 · Ainer</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif;
                           background: #F3F6FA; color: #172033; margin: 0; display: flex; justify-content: center;
                           align-items: center; min-height: 100vh; }
                    .card { background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(16,46,86,.12);
                            padding: 40px; max-width: 440px; width: calc(100%% - 40px); }
                    h1 { font-size: 24px; font-weight: 600; margin: 0 0 8px; }
                    p.subtitle { color: #566276; margin: 0 0 24px; font-size: 14px; }
                    .tenant-option { display: flex; align-items: center; gap: 8px; padding: 12px 16px;
                                     border: 1px solid #CAD2DE; border-radius: 8px; margin-bottom: 8px;
                                     cursor: pointer; }
                    .tenant-option:hover { border-color: #2458A6; }
                    .tenant-option input { accent-color: #2458A6; }
                    .tenant-name { font-weight: 600; }
                    .tenant-code { color: #566276; font-size: 13px; }
                    .tenant-role { margin-left: auto; font-size: 12px; color: #566276; }
                    button { width: 100%%; padding: 12px; background: #2458A6; color: #fff; border: none;
                             border-radius: 8px; font-size: 15px; font-weight: 600; cursor: pointer; margin-top: 16px; }
                    button:hover { background: #173A69; }
                    .error { background: #FFF1F0; color: #A8071A; padding: 12px 16px; border-radius: 8px;
                             margin-bottom: 16px; font-size: 14px; }
                    footer { text-align: center; color: #566276; font-size: 12px; margin-top: 24px; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <h1>选择租户</h1>
                    <p class="subtitle">选择本次会话使用的工作空间。</p>
                    %s
                    <form method="post" action="%s/select-tenant">
                      <input type="hidden" name="%s" value="%s">
                      %s
                      <button type="submit">继续</button>
                    </form>
                    <footer>身份验证由 Ainer Boot 提供</footer>
                  </main>
                </body>
                </html>
                """.formatted(
                errorBlock,
                HtmlUtils.htmlEscape(contextPath),
                HtmlUtils.htmlEscape(csrfToken.getParameterName()),
                HtmlUtils.htmlEscape(csrfToken.getToken()),
                options.toString());
    }
}
