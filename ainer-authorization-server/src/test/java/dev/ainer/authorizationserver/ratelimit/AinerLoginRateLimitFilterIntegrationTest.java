package dev.ainer.authorizationserver.ratelimit;

import dev.ainer.authorizationserver.login.AinerLoginPageRenderer;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AinerLoginRateLimitFilterIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.context-path=/auth",
                "spring.main.banner-mode=off",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        })
class AinerLoginRateLimitFilterIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void postMatcherHonorsContextPathAndReturnsStandard429Contract() throws Exception {
        HttpResponse<String> first = request("POST");
        HttpResponse<String> second = request("POST");
        HttpResponse<String> get = request("GET");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(429);
        assertThat(second.headers().firstValue("Retry-After")).hasValue("60");
        assertThat(second.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(second.body())
                .contains("\"code\":\"AINER.COMMON.RATE_LIMITED\"")
                .contains("\"requestId\"")
                .contains("\"timestamp\"");
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(meterRegistry.get("ainer.security.login.rate-limit.allowed").counter().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("ainer.security.login.rate-limit.denied").counter().count())
                .isEqualTo(1);
    }

    private HttpResponse<String> request(String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/auth/login".formatted(port)))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({LoginEndpoint.class, SecurityTestConfiguration.class})
    static class TestApplication {
    }

    @RestController
    static class LoginEndpoint {

        @PostMapping("/login")
        ApiResponse<String> login(HttpServletRequest request) {
            return ApiResponse.success("accepted", RequestIds.currentOrCreate(request));
        }

        @GetMapping("/login")
        ApiResponse<String> loginPage(HttpServletRequest request) {
            return ApiResponse.success("login", RequestIds.currentOrCreate(request));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        AinerLoginRateLimitFilter loginRateLimitFilter(
                ObjectMapper objectMapper, SimpleMeterRegistry meterRegistry) {
            Clock clock = Clock.fixed(Instant.parse("2026-07-26T03:00:00Z"), ZoneOffset.UTC);
            return new AinerLoginRateLimitFilter(
                    new AinerRateLimiter(Duration.ofMinutes(1), 1, clock),
                    Set.of("/login"),
                    new AinerSecurityFailureWriter(objectMapper),
                    new AinerLoginPageRenderer(),
                    meterRegistry);
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http, AinerLoginRateLimitFilter filter) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .addFilterAfter(filter, SecurityContextHolderFilter.class);
            return http.build();
        }
    }
}
