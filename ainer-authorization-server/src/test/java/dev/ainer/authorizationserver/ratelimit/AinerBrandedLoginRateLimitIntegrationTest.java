package dev.ainer.authorizationserver.ratelimit;

import dev.ainer.authorizationserver.login.AinerLoginPageController;
import dev.ainer.authorizationserver.login.AinerLoginPageRenderer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AinerBrandedLoginRateLimitIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        })
class AinerBrandedLoginRateLimitIntegrationTest {

    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>");

    @LocalServerPort
    private int port;

    @Test
    void validCsrfHtmlSubmissionReceivesBranded429WithRetryAfter() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> page = client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header(HttpHeaders.ACCEPT, "text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher csrf = CSRF_INPUT.matcher(page.body());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(csrf.find()).isTrue();

        HttpResponse<String> first = submit(client, csrf.group(1));
        HttpResponse<String> second = submit(client, csrf.group(1));

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(429);
        assertThat(second.headers().firstValue(HttpHeaders.RETRY_AFTER)).hasValue("60");
        assertThat(second.headers().firstValue(HttpHeaders.CACHE_CONTROL)
                        .orElseThrow())
                .contains("no-store");
        assertThat(second.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                        .orElseThrow())
                .startsWith("text/html");
        assertThat(second.body())
                .contains("data-state=\"rate-limited\"")
                .contains("登录尝试过于频繁，请稍后再试。");
    }

    private HttpResponse<String> submit(HttpClient client, String csrf) throws Exception {
        String body = "_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)
                + "&username=user&password=secret";
        return client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                        .header(
                                HttpHeaders.CONTENT_TYPE,
                                "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:%d%s".formatted(port, path));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            LoginEndpoint.class,
            SecurityTestConfiguration.class,
            AinerLoginPageController.class,
            AinerLoginPageRenderer.class
    })
    static class TestApplication {
    }

    @RestController
    static class LoginEndpoint {

        @PostMapping("/login")
        String login(HttpServletRequest request) {
            return "accepted";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        AinerLoginRateLimitFilter loginRateLimitFilter(
                AinerLoginPageRenderer renderer) {
            Clock clock =
                    Clock.fixed(Instant.parse("2026-07-27T05:00:00Z"), ZoneOffset.UTC);
            return new AinerLoginRateLimitFilter(
                    new AinerRateLimiter(Duration.ofMinutes(1), 1, clock),
                    Set.of("/login"),
                    null,
                    renderer,
                    null);
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                AinerLoginRateLimitFilter filter) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .addFilterAfter(filter, CsrfFilter.class);
            return http.build();
        }
    }
}
