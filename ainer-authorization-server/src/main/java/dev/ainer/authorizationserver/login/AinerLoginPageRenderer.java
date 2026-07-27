package dev.ainer.authorizationserver.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public final class AinerLoginPageRenderer {

    private static final List<String> MFA_QUERY_PARAMETERS =
            List.of("factor.type", "factor.reason");
    private static final String TEMPLATE = loadTemplate();

    public void render(
            HttpServletRequest request,
            HttpServletResponse response,
            AinerLoginPageState state,
            int status) throws IOException {
        CsrfToken csrfToken = csrfToken(request);
        String contextPath = request.getContextPath();
        String formAction = loginFormAction(request, contextPath);

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.getWriter().write(TEMPLATE
                .replace("{{stateKey}}", state.key())
                .replace("{{contextPath}}", HtmlUtils.htmlEscape(contextPath))
                .replace("{{formAction}}", HtmlUtils.htmlEscape(formAction))
                .replace("{{csrfParameterName}}",
                        HtmlUtils.htmlEscape(csrfToken.getParameterName()))
                .replace("{{csrfToken}}", HtmlUtils.htmlEscape(csrfToken.getToken()))
                .replace("{{statusContent}}", statusContent(state)));
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

    private static String loginFormAction(
            HttpServletRequest request,
            String contextPath) {
        UriComponentsBuilder action =
                UriComponentsBuilder.fromPath(contextPath + "/login");
        MFA_QUERY_PARAMETERS.forEach(parameter -> {
            String value = request.getParameter(parameter);
            if (value != null && !value.isBlank()) {
                action.queryParam(parameter, value);
            }
        });
        return action.build().encode().toUriString();
    }

    private static String statusContent(AinerLoginPageState state) {
        if (state == AinerLoginPageState.NORMAL) {
            return "<div class=\"ainer-login__status "
                    + "ainer-login__status--empty\" aria-hidden=\"true\"></div>";
        }
        return """
                <div class="ainer-login__status ainer-login__status--%s" role="alert">
                  <strong class="ainer-login__status-title">%s</strong>
                  <span class="ainer-login__status-message">%s</span>
                </div>
                """.formatted(
                state.severity(),
                HtmlUtils.htmlEscape(state.title()),
                HtmlUtils.htmlEscape(state.message()));
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource("ainer-login/login.html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot load Ainer login template", exception);
        }
    }
}
