package dev.ainer.module.notification;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JWT HTTP test for the notification management API (ADR-0040): template lifecycle, direct
 * submission, PII-safe record listings and scope enforcement.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = NotificationHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.notification.enabled=true",
                "ainer.notification.poll-interval-ms=999999",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class NotificationHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_notification_http_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_notification_audit");
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
        authenticateWith(managerToken());
    }

    private String managerToken() {
        return JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "notification.read notification.manage notification.submit");
    }

    @Test
    void pageTemplatesWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(List.of());

        ResponseEntity<String> response = restTemplate.getForEntity(
                uri("/api/notifications/templates"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void manageWithoutScopeIsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "notification.read"));

        ResponseEntity<String> response = restTemplate.exchange(uri("/api/notifications/templates"),
                HttpMethod.POST, new org.springframework.http.HttpEntity<>("""
                        {"code": "x", "channel": "EMAIL", "titleTemplate": "T", "bodyTemplate": "B"}
                        """, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void templateAndDirectAndRecordsLifecycleOverHttp() {
        RestResponse created = postJson("/api/notifications/templates", """
                {"code": "welcome", "channel": "EMAIL",
                 "titleTemplate": "Hi {name}", "bodyTemplate": "Hello {name}",
                 "variablesSchema": {"name": "string"}}
                """);
        assertThat(created.status().value()).isEqualTo(201);
        String id = (String) created.jsonPath("$.data.id");

        RestResponse updated = exchange("/api/notifications/templates/" + id, HttpMethod.PUT, """
                {"titleTemplate": "Hey {name}", "expectedVersion": 0}
                """);
        assertThat(updated.status().value()).isEqualTo(200);
        assertThat(updated.jsonPath("$.data.version")).isEqualTo(1);

        RestResponse stale = exchange("/api/notifications/templates/" + id, HttpMethod.PUT, """
                {"titleTemplate": "X", "expectedVersion": 0}
                """);
        assertThat(stale.status().value()).isEqualTo(409);

        RestResponse submitted = postJson("/api/notifications/messages", """
                {"channel": "EMAIL", "recipient": "user@test.com", "title": "T", "body": "B"}
                """);
        assertThat(submitted.status().value()).isEqualTo(201);

        RestResponse records = get("/api/notifications/records?status=PENDING");
        assertThat(records.status().value()).isEqualTo(200);
        assertThat(records.jsonPath("$.data.total")).isEqualTo(1);
        // record listings omit rendered content (PII)
        assertThat(records.body()).doesNotContain("\"title\":\"T\"");
        assertThat(records.body()).doesNotContain("\"body\":\"B\"");

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_notification_audit", Integer.class);
        assertThat(audits).isEqualTo(2); // TEMPLATE_CREATED + TEMPLATE_UPDATED
    }

    // --------------------------------------------------------------- helpers

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private RestResponse postJson(String path, String json) {
        return exchange(path, HttpMethod.POST, json);
    }

    private RestResponse get(String path) {
        return new RestResponse(restTemplate.getForEntity(uri(path), String.class));
    }

    private RestResponse exchange(String path, HttpMethod method, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new RestResponse(restTemplate.exchange(uri(path), method,
                new org.springframework.http.HttpEntity<>(json, headers), String.class));
    }

    private void authenticateWith(String jwt) {
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        }));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(NotificationModuleConfiguration.class)
    static class TestApplication {

        @Bean
        JwtDecoder notificationHttpJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @Bean
        @Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver notificationHttpPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }
    }
}
