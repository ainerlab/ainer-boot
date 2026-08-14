package dev.ainer.module.config;

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
 * Real-JWT HTTP test for the config management API (ADR-0040): set values/secrets, masked listing
 * (secrets never echo values), change history and scope enforcement.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ConfigHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.config.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class ConfigHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_config_http_test")
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
        jdbcTemplate.execute("DELETE FROM ainer_config_history");
        jdbcTemplate.execute("DELETE FROM ainer_config_entry");
        authenticateWith(managerToken());
    }

    private String managerToken() {
        return JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "config.read config.manage");
    }

    @Test
    void listWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(List.of());

        ResponseEntity<String> response = restTemplate.getForEntity(
                uri("/api/configs/entries?namespace=app"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void setWithoutManageScopeIsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "config.read"));

        ResponseEntity<String> response = restTemplate.exchange(uri("/api/configs/entries"),
                HttpMethod.POST, new org.springframework.http.HttpEntity<>("""
                        {"namespace": "app", "key": "k", "value": "v", "valueType": "STRING"}
                        """, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void valueAndSecretLifecycleOverHttp() {
        assertThat(postJson("/api/configs/entries", """
                {"namespace": "app", "key": "site.name", "value": "Ainer", "valueType": "STRING"}
                """).status().value()).isEqualTo(201);
        assertThat(postJson("/api/configs/secrets", """
                {"namespace": "app", "key": "db.password", "plaintext": "s3cr3t", "valueType": "STRING"}
                """).status().value()).isEqualTo(201);

        RestResponse entries = get("/api/configs/entries?namespace=app");
        assertThat(entries.status().value()).isEqualTo(200);
        assertThat(entries.jsonPath("$.data.items.length()")).isEqualTo(2);
        // entries are ordered by key: "db.password" < "site.name" — the secret entry is masked
        assertThat(entries.jsonPath("$.data.items[0].secret")).isEqualTo(true);
        assertThat(entries.jsonPath("$.data.items[1].secret")).isEqualTo(false);
        String body = entries.body();
        assertThat(body).contains("site.name");
        assertThat(body).contains("\"value\":\"Ainer\"");
        assertThat(body).doesNotContain("s3cr3t");

        // plaintext write onto a secret key is rejected with 409
        RestResponse conflict = postJson("/api/configs/entries", """
                {"namespace": "app", "key": "db.password", "value": "plain", "valueType": "STRING"}
                """);
        assertThat(conflict.status().value()).isEqualTo(409);
        assertThat(conflict.jsonPath("$.code"))
                .isEqualTo("AINER.CONFIG.PLAINTEXT_ON_SECRET_KEY");

        // history records both writes with the encrypted marker instead of plaintext
        RestResponse history = get("/api/configs/history?namespace=app&key=db.password");
        assertThat(history.status().value()).isEqualTo(200);
        assertThat(history.jsonPath("$.data.items[0].newValue")).isEqualTo("[encrypted]");
    }

    // --------------------------------------------------------------- helpers

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private RestResponse postJson(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new RestResponse(restTemplate.exchange(uri(path), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(json, headers), String.class));
    }

    private RestResponse get(String path) {
        return new RestResponse(restTemplate.getForEntity(uri(path), String.class));
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
    @Import(ConfigModuleConfiguration.class)
    static class TestApplication {

        @Bean
        JwtDecoder configHttpJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @Bean
        @Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver configHttpPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }
    }
}
