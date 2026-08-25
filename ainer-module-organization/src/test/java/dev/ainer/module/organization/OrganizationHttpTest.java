package dev.ainer.module.organization;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.module.organization.OrganizationModuleConfiguration;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JWT HTTP test for the organization management API (ADR-0042 O1). Uses the shared
 * {@link JwtTestSupport} fixture: test RSA key signs USER_NEUTRAL_V1 tokens, a real
 * {@link JwtDecoder} verifies them through the resource-server chain, and the
 * production-equivalent resolver resolves the typed principal.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = OrganizationHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.organization.enabled=true",
                "ainer.organization.trusted-issuer=https://auth.ainer.test",
                // 本切片只验 JWT+scope；@AinerAuthorize 由 LivePath / SubjectSet 流测覆盖
                "ainer.authorization.enabled=false",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class OrganizationHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c3000-0000-7000-8000-0000000000aa");
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_org_http_test")
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

    private RestTestClient client;

    @BeforeEach
    void cleanAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM ainer_org_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_org_position_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_position");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_engagement");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_parent");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit");
        jdbcTemplate.execute("DELETE FROM ainer_org_directory");
        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticateAsManager();
    }

    private void authenticateAsManager() {
        String jwt = JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "account:admin", "organization.read organization.manage");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private String createDirectory(String code) {
        RestResponse response = client.postJson("/api/organization/directories", """
                {"workspaceId": "%s", "code": "%s", "displayName": "目录 %s"}
                """.formatted(WORKSPACE_ID, code, code));
        assertThat(response.status().value()).isEqualTo(201);
        return (String) response.jsonPath("$.data.id");
    }

    @Test
    void pageWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());

        ResponseEntity<String> response = restTemplate.getForEntity(
                uri("/api/organization/directories?workspaceId=" + WORKSPACE_ID), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void requestWithoutScopeIsForbidden() {
        String jwt = JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "account:1", "unrelated.read");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        ResponseEntity<String> response = restTemplate.exchange(
                uri("/api/organization/directories?workspaceId=" + WORKSPACE_ID),
                HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void createDirectoryReturns201AndAuditsBothEntities() {
        RestResponse created = client.postJson("/api/organization/directories", """
                {"workspaceId": "%s", "code": "main", "displayName": "主目录"}
                """.formatted(WORKSPACE_ID));

        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.jsonPath("$.code")).isEqualTo("AINER.COMMON.OK");
        assertThat((String) created.jsonPath("$.data.id")).isNotBlank();
        assertThat(created.header("X-Request-Id")).isNotBlank();

        Integer directoryAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit WHERE entity_type = 'DIRECTORY'",
                Integer.class);
        Integer unitAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit WHERE entity_type = 'UNIT'",
                Integer.class);
        assertThat(directoryAudits).isEqualTo(1);
        assertThat(unitAudits).isEqualTo(1);

        RestResponse duplicate = client.postJson("/api/organization/directories", """
                {"workspaceId": "%s", "code": "main", "displayName": "重复"}
                """.formatted(WORKSPACE_ID));
        assertThat(duplicate.status().value()).isEqualTo(409);
        assertThat(duplicate.jsonPath("$.code"))
                .isEqualTo("AINER.ORGANIZATION.DUPLICATE_DIRECTORY_CODE");
    }

    @Test
    void engagementLifecycleWithSuspensionEndpoint() {
        String directoryId = createDirectory("d");
        RestResponse engaged = client.postJson(
                "/api/organization/directories/" + directoryId + "/engagements", """
                {"subjectIssuer": "%s", "subjectId": "account:1", "engagementType": "EMPLOYEE",
                 "employeeNumber": "E001", "validFrom": "2026-01-01T00:00:00Z"}
                """.formatted(ISSUER));
        assertThat(engaged.status().value()).isEqualTo(201);
        String engagementId = (String) engaged.jsonPath("$.data.id");

        RestResponse overlap = client.postJson(
                "/api/organization/directories/" + directoryId + "/engagements", """
                {"subjectIssuer": "%s", "subjectId": "account:1", "engagementType": "EMPLOYEE",
                 "validFrom": "2026-06-01T00:00:00Z"}
                """.formatted(ISSUER));
        assertThat(overlap.status().value()).isEqualTo(409);
        assertThat(overlap.jsonPath("$.code"))
                .isEqualTo("AINER.ORGANIZATION.ENGAGEMENT_PERIOD_OVERLAP");

        RestResponse suspended = client.postJson(
                "/api/organization/directories/" + directoryId
                        + "/engagements/" + engagementId + "/suspensions", "{}");
        assertThat(suspended.status().value()).isEqualTo(200);
        assertThat(suspended.jsonPath("$.data.status")).isEqualTo("SUSPENDED");

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit WHERE entity_type = 'ENGAGEMENT'",
                Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void engagementWithUntrustedIssuerIsRejected() {
        String directoryId = createDirectory("d");
        RestResponse response = client.postJson(
                "/api/organization/directories/" + directoryId + "/engagements", """
                {"subjectIssuer": "https://evil.example", "subjectId": "account:1",
                 "engagementType": "EMPLOYEE", "validFrom": "2026-01-01T00:00:00Z"}
                """);
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.ORGANIZATION.INVALID_ISSUER");
    }

    @Test
    void memberProjectionReflectsLiveResolution() {
        String directoryId = createDirectory("d");
        RestResponse rootUnits = client.get(
                "/api/organization/directories/" + directoryId + "/units");
        String rootUnitId = (String) rootUnits.jsonPath("$.data[0].id");

        RestResponse unitCreated = client.postJson(
                "/api/organization/directories/" + directoryId + "/units", """
                {"parentUnitId": "%s", "code": "ops", "displayName": "运营"}
                """.formatted(rootUnitId));
        assertThat(unitCreated.status().value()).isEqualTo(201);
        String unitId = (String) unitCreated.jsonPath("$.data.id");

        RestResponse engaged = client.postJson(
                "/api/organization/directories/" + directoryId + "/engagements", """
                {"subjectIssuer": "%s", "subjectId": "account:42", "engagementType": "EMPLOYEE",
                 "validFrom": "2026-01-01T00:00:00Z"}
                """.formatted(ISSUER));
        String engagementId = (String) engaged.jsonPath("$.data.id");

        RestResponse assigned = client.postJson(
                "/api/organization/directories/" + directoryId + "/unit-assignments", """
                {"engagementId": "%s", "orgUnitId": "%s", "kind": "PRIMARY",
                 "validFrom": "2026-01-01T00:00:00Z"}
                """.formatted(engagementId, unitId));
        assertThat(assigned.status().value()).isEqualTo(201);

        RestResponse members = client.get("/api/organization/directories/" + directoryId
                + "/units/" + unitId + "/members?atTime=2026-02-01T00:00:00Z");
        assertThat(members.status().value()).isEqualTo(200);
        assertThat(members.jsonPath("$.data.length()")).isEqualTo(1);
        assertThat(members.jsonPath("$.data[0].subjectId")).isEqualTo("account:42");

        // 终止后同一时刻的成员投影立即为空（决策时实时解析）
        client.postJson("/api/organization/directories/" + directoryId
                + "/engagements/" + engagementId + "/terminations", "{}");
        RestResponse afterTermination = client.get("/api/organization/directories/" + directoryId
                + "/units/" + unitId + "/members?atTime=2026-02-01T00:00:00Z");
        assertThat(afterTermination.jsonPath("$.data.length()")).isEqualTo(0);
    }

    private String uri(String path) {
        return "http://localhost:" + port + path;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrganizationModuleConfiguration.class)
    static class TestApplication {

        @Bean
        JwtDecoder organizationTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @Bean
        @Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver organizationTestPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }
    }
}
