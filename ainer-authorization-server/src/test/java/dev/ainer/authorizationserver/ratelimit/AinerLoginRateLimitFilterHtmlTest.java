package dev.ainer.authorizationserver.ratelimit;

import dev.ainer.authorizationserver.login.AinerLoginPageRenderer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AinerLoginRateLimitFilterHtmlTest {

    @Test
    void htmlLoginSubmissionRendersBranded429WithoutChangingApiContract() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-27T04:00:00Z"), ZoneOffset.UTC);
        AinerLoginRateLimitFilter filter = new AinerLoginRateLimitFilter(
                new AinerRateLimiter(Duration.ofMinutes(1), 1, clock),
                Set.of("/login"),
                null,
                new AinerLoginPageRenderer(),
                null);
        AtomicInteger accepted = new AtomicInteger();
        FilterChain chain = (request, response) -> accepted.incrementAndGet();

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request(), first, chain);
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request(), second, chain);

        assertThat(accepted).hasValue(1);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(second.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(second.getContentType()).startsWith("text/html");
        assertThat(second.getContentAsString())
                .contains("data-state=\"rate-limited\"")
                .contains("登录尝试过于频繁，请稍后再试。");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.addHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        CsrfToken token =
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "server-token");
        request.setAttribute(CsrfToken.class.getName(), token);
        return request;
    }
}
