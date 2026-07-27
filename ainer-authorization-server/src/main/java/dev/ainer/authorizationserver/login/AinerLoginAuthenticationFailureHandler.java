package dev.ainer.authorizationserver.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Component
public final class AinerLoginAuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    private static final List<String> MFA_QUERY_PARAMETERS =
            List.of("factor.type", "factor.reason");

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException, ServletException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");

        UriComponentsBuilder target =
                UriComponentsBuilder.fromPath(request.getContextPath() + "/login");
        if (exception instanceof AuthenticationServiceException) {
            request.getSession().setAttribute(
                    AinerLoginPageController.SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE,
                    Boolean.TRUE);
        } else {
            target.queryParam("error");
        }
        MFA_QUERY_PARAMETERS.forEach(parameter -> {
            String value = request.getParameter(parameter);
            if (value != null && !value.isBlank()) {
                target.queryParam(parameter, value);
            }
        });
        redirectStrategy.sendRedirect(
                request,
                response,
                target.build().encode().toUriString());
    }
}
