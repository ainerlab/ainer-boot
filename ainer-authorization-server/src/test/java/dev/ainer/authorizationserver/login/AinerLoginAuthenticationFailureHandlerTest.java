package dev.ainer.authorizationserver.login;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class AinerLoginAuthenticationFailureHandlerTest {

    private final AinerLoginAuthenticationFailureHandler handler =
            new AinerLoginAuthenticationFailureHandler();

    @Test
    void badCredentialsUseGenericErrorRedirectAndPreserveMfaContext() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/login");
        request.setParameter("factor.type", "password");
        request.setParameter("factor.reason", "step-up");
        request.setParameter("username", "must-not-leak");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("bad password"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/login?error&factor.type=password&factor.reason=step-up")
                .doesNotContain("must-not-leak");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void onlyAuthenticationServiceExceptionCreates503Signal() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new AuthenticationServiceException("identity store unavailable"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
        assertThat(request.getSession(false)
                        .getAttribute(AinerLoginPageController
                                .SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
    }
}
