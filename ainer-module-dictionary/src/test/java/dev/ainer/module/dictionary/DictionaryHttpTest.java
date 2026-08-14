package dev.ainer.module.dictionary;

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
 * Real-JWT HTTP test for the dictionary management API (ADR-0040). The JWT is signed with a test
 * RSA key and verified by a real {@link JwtDecoder} through the resource-server chain; scopes
 * ({@code dictionary.read}/{@code dictionary.manage}) are enforced in the service.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = DictionaryHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.dictionary.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class DictionaryHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_dictionary_http_test")
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
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_audit");
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_item");
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_type");
        authenticateWith(managerToken());
    }

    private String managerToken() {
        return JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "dictionary.read dictionary.manage");
    }

    @Test
    void pageWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(List.of());

        ResponseEntity<String> response = restTemplate.getForEntity(uri("/api/dictionaries/types"),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void manageWithoutScopeIsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, "account:1",
                "dictionary.read"));

        ResponseEntity<String> response = restTemplate.exchange(uri("/api/dictionaries/types"),
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>("""
                        {"code": "x", "name": "X"}
                        """, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void typeLifecycleOverHttp() {
        RestResponse created = postJson("/api/dictionaries/types", """
                {"code": "gender", "name": "性别", "nameEn": "Gender"}
                """);
        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.jsonPath("$.code")).isEqualTo("AINER.COMMON.OK");
        String id = (String) created.jsonPath("$.data.id");

        RestResponse updated = exchange("/api/dictionaries/types/" + id, HttpMethod.PUT, """
                {"name": "性别2", "sortIndex": 3, "expectedVersion": 0}
                """);
        assertThat(updated.status().value()).isEqualTo(200);
        assertThat(updated.jsonPath("$.data.version")).isEqualTo(1);
        assertThat(updated.jsonPath("$.data.sortIndex")).isEqualTo(3);

        // stale version → 409
        RestResponse stale = exchange("/api/dictionaries/types/" + id, HttpMethod.PUT, """
                {"name": "性别3", "expectedVersion": 0}
                """);
        assertThat(stale.status().value()).isEqualTo(409);
        assertThat(stale.jsonPath("$.code")).isEqualTo("AINER.DICTIONARY.CONCURRENT_MODIFICATION");

        RestResponse disabled = postJson("/api/dictionaries/types/" + id + "/status-changes", """
                {"status": "DISABLED", "expectedVersion": 1}
                """);
        assertThat(disabled.status().value()).isEqualTo(200);
        assertThat(disabled.jsonPath("$.data.status")).isEqualTo("DISABLED");

        RestResponse page = get("/api/dictionaries/types?status=ACTIVE");
        assertThat(page.status().value()).isEqualTo(200);
        assertThat(page.jsonPath("$.data.total")).isEqualTo(0);

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_dictionary_audit", Integer.class);
        assertThat(audits).isEqualTo(3); // CREATED + UPDATED + STATUS_CHANGED
    }

    @Test
    void duplicateTypeCodeReturnsConflict() {
        postJson("/api/dictionaries/types", """
                {"code": "dup", "name": "一"}
                """);
        RestResponse duplicate = postJson("/api/dictionaries/types", """
                {"code": "dup", "name": "二"}
                """);
        assertThat(duplicate.status().value()).isEqualTo(409);
        assertThat(duplicate.jsonPath("$.code")).isEqualTo("AINER.DICTIONARY.TYPE_ALREADY_EXISTS");
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
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
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
    @Import(DictionaryModuleConfiguration.class)
    static class TestApplication {

        @Bean
        JwtDecoder dictionaryHttpJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @Bean
        @Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver dictionaryHttpPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }
    }
}
