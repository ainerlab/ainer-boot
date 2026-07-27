package dev.ainer.authorizationserver.login;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AinerLoginPageControllerTest {

    private static final CsrfToken CSRF_TOKEN =
            new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "server-token");
    private static final Pattern LEVEL_ONE_HEADING =
            Pattern.compile("<h1(?:\\s|>)", Pattern.CASE_INSENSITIVE);

    private final AinerLoginPageRenderer renderer = new AinerLoginPageRenderer();
    private final AinerLoginPageController controller =
            new AinerLoginPageController(renderer);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void normalPageUsesServerCsrfAndApprovedStructure() throws Exception {
        org.springframework.mock.web.MockHttpServletResponse response =
                mockMvc.perform(get("/login")
                                .requestAttr(CsrfToken.class.getName(), CSRF_TOKEN))
                        .andReturn()
                        .getResponse();
        String body = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(body)
                .contains("lang=\"zh-CN\"")
                .contains("data-state=\"normal\"")
                .contains("<h1 id=\"ainer-login-title\">登录 Ainer</h1>")
                .contains("name=\"_csrf\" value=\"server-token\"")
                .contains("autocomplete=\"username\"")
                .contains("autocomplete=\"current-password\"")
                .contains("身份验证由 Ainer Boot 提供")
                .doesNotContain("role=\"alert\"")
                .doesNotContain("value=\"admin\"");
        assertThat(LEVEL_ONE_HEADING.matcher(body).results()).hasSize(1);
    }

    @Test
    void credentialFailureUsesOneGenericMessage() throws Exception {
        org.springframework.mock.web.MockHttpServletResponse response =
                mockMvc.perform(get("/login")
                                .param("error", "")
                                .requestAttr(CsrfToken.class.getName(), CSRF_TOKEN))
                        .andReturn()
                        .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentAsString())
                .contains("data-state=\"credential-error\"")
                .contains("用户名或密码错误，请重新输入。")
                .contains("role=\"alert\"");
    }

    @Test
    void infrastructureFailureIsConsumedOnceAndReturns503() throws Exception {
        org.springframework.mock.web.MockHttpSession session =
                new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(
                AinerLoginPageController.SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE,
                Boolean.TRUE);

        org.springframework.mock.web.MockHttpServletResponse unavailable =
                mockMvc.perform(get("/login")
                                .session(session)
                                .requestAttr(CsrfToken.class.getName(), CSRF_TOKEN))
                        .andReturn()
                        .getResponse();
        assertThat(unavailable.getStatus()).isEqualTo(503);
        assertThat(unavailable.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(unavailable.getContentAsString())
                .contains("data-state=\"service-unavailable\"")
                .contains("登录服务暂时不可用");

        org.springframework.mock.web.MockHttpServletResponse recovered =
                mockMvc.perform(get("/login")
                                .session(session)
                                .requestAttr(CsrfToken.class.getName(), CSRF_TOKEN))
                        .andReturn()
                        .getResponse();
        assertThat(recovered.getStatus()).isEqualTo(200);
        assertThat(recovered.getContentAsString()).contains("data-state=\"normal\"");
    }

    @Test
    void mfaParametersAreWhitelistedEscapedAndPreservedInFormAction() throws Exception {
        String body = mockMvc.perform(get("/login")
                        .param("factor.type", "password")
                        .param("factor.reason", "step up & retry")
                        .param("client_id", "must-not-leak")
                        .requestAttr(CsrfToken.class.getName(), CSRF_TOKEN))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("action=\"/login?factor.type=password&amp;"
                        + "factor.reason=step%20up%20%26%20retry\"")
                .doesNotContain("client_id=must-not-leak");
    }

    @Test
    void rateLimitedViewKeepsHttpSemantics() throws Exception {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/login");
        request.setAttribute(CsrfToken.class.getName(), CSRF_TOKEN);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        renderer.render(
                request,
                response,
                AinerLoginPageState.RATE_LIMITED,
                429);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentAsString())
                .contains("data-state=\"rate-limited\"")
                .contains("登录尝试过于频繁，请稍后再试。")
                .contains("role=\"alert\"");
    }
}
