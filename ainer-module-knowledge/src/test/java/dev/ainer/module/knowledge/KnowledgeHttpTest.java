package dev.ainer.module.knowledge;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.module.knowledge.KnowledgeModuleConfiguration;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JWT HTTP tests for the Knowledge Foundation (ADR-0044 K1/K2). Money assertions:
 * SERVICE/AI may propose but publishing is a human gate (403); unpublished revisions are
 * invisible to asOf resolution; supersession pins exact revisions.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = KnowledgeHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.knowledge.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class KnowledgeHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c6000-0000-7000-8000-000000000001");
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_knowledge_http_test")
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
    void cleanAndAuthenticateAsHuman() {
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_lifecycle_event");
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_revision_lineage");
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_evidence");
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_source");
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_revision");
        jdbcTemplate.execute("DELETE FROM ainer_knowledge_object");
        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticateAs(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "knowledge-curator", "knowledge.read knowledge.manage"));
    }

    private void authenticateAs(String jwt) {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private String serviceJwt() {
        return JwtTestSupport.signServiceJwt(RSA_JWK, ISSUER, AUDIENCE, "ai-writer",
                "knowledge.read knowledge.manage");
    }

    private String createObject() {
        RestResponse created = client.postJson("/api/knowledge/objects", """
                {"workspaceId": "%s", "kind": "jade.glossary", "title": "翡翠 A 货定义"}
                """.formatted(WORKSPACE_ID));
        assertThat(created.status().value()).isEqualTo(201);
        return (String) created.jsonPath("$.data.id");
    }

    private String propose(String objectId, String payload, String basedOnJson) {
        RestResponse proposed = client.postJson("/api/knowledge/objects/" + objectId + "/revisions", """
                {"payloadMarkdown": "%s", "sources": [{"sourceType": "manual.gemlab",
                 "sourceRef": "report-2026-001"}],
                 "evidence": [{"linkType": "SUPPORTS", "targetRef": "gemlab:cert:889"}]%s}
                """.formatted(payload, basedOnJson));
        assertThat(proposed.status().value())
                .as("propose -> %s", proposed.body()).isEqualTo(201);
        return (String) proposed.jsonPath("$.data.id");
    }

    private Instant publishedAtOf(String revisionId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT published_at FROM ainer_knowledge_revision WHERE id = ?",
                Timestamp.class, UUID.fromString(revisionId));
        return timestamp.toInstant();
    }

    @Test
    void pageWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/knowledge/objects?workspaceId=" + WORKSPACE_ID,
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void requestWithoutScopeIsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "account:1", "unrelated.read"));
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/knowledge/objects?workspaceId=" + WORKSPACE_ID,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void invalidKindIsRejected() {
        RestResponse response = client.postJson("/api/knowledge/objects", """
                {"workspaceId": "%s", "kind": "not-namespaced", "title": "x"}
                """.formatted(WORKSPACE_ID));
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.KNOWLEDGE.INVALID_KIND");
    }

    @Test
    void serviceMayProposeButPublishingIsAHumanGate() {
        String objectId = createObject();
        authenticateAs(serviceJwt());
        String revisionId = propose(objectId, "AI 草稿：翡翠处理定义（提案）", "");
        assertThat((Integer) client.get("/api/knowledge/revisions/" + revisionId)
                .jsonPath("$.data.revisionNumber")).isEqualTo(1);
        assertThat(client.get("/api/knowledge/revisions/" + revisionId)
                .jsonPath("$.data.createdByType")).isEqualTo("SERVICE");

        // SERVICE/AI 发布 → 403（ADR-0044 不变式 #9）
        RestResponse aiPublish = client.postJson(
                "/api/knowledge/revisions/" + revisionId + "/publications", "{}");
        assertThat(aiPublish.status().value()).isEqualTo(403);
        assertThat(aiPublish.jsonPath("$.code"))
                .isEqualTo("AINER.KNOWLEDGE.PUBLISH_REQUIRES_HUMAN");

        // 人工发布 → 200；重复发布 → 409
        authenticateAs(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "knowledge-curator", "knowledge.read knowledge.manage"));
        RestResponse published = client.postJson(
                "/api/knowledge/revisions/" + revisionId + "/publications", "{}");
        assertThat(published.status().value()).isEqualTo(200);
        assertThat(published.jsonPath("$.data.status")).isEqualTo("PUBLISHED");
        RestResponse again = client.postJson(
                "/api/knowledge/revisions/" + revisionId + "/publications", "{}");
        assertThat(again.status().value()).isEqualTo(409);

        // 生命周期事件 append-only：OBJECT_CREATED + PROPOSED + PUBLISHED + PUBLISHED(拒绝前)
        Long events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ainer_knowledge_lifecycle_event WHERE object_id = ?",
                Long.class, UUID.fromString(objectId));
        assertThat(events).isEqualTo(3L);
    }

    @Test
    void unpublishedRevisionIsInvisibleAndAsOfPinsExactRevision() {
        String objectId = createObject();
        String v1 = propose(objectId, "v1 正文", "");
        authenticateAs(serviceJwt());

        // 未发布 → 解析 404（对读取不可见）
        RestResponse beforePublish = client.get("/api/knowledge/objects/" + objectId);
        assertThat(beforePublish.status().value()).isEqualTo(404);

        authenticateAs(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE,
                "knowledge-curator", "knowledge.read knowledge.manage"));
        client.postJson("/api/knowledge/revisions/" + v1 + "/publications", "{}");

        // supersede：基于 v1 提案 v2（lineage 显式）
        String v2 = propose(objectId, "v2 修订正文",
                ", \"basedOnRevisionId\": \"" + v1 + "\"");
        client.postJson("/api/knowledge/revisions/" + v2 + "/publications", "{}");

        Long lineage = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ainer_knowledge_revision_lineage WHERE to_revision_id = ?",
                Long.class, UUID.fromString(v2));
        assertThat(lineage).isEqualTo(1L);

        // 默认解析 → v2；v1 发布时刻之后的 1µs → v1；v1 发布前 1µs → 404（不可见）
        assertThat((Integer) client.get("/api/knowledge/objects/" + objectId)
                .jsonPath("$.data.revisionNumber")).isEqualTo(2);
        Instant v1Published = publishedAtOf(v1);
        RestResponse atV1 = client.get("/api/knowledge/objects/" + objectId
                + "?asOf=" + v1Published.plusNanos(1_000));
        assertThat(atV1.status().value()).isEqualTo(200);
        assertThat(atV1.jsonPath("$.data.id")).isEqualTo(v1);
        RestResponse beforeV1 = client.get("/api/knowledge/objects/" + objectId
                + "?asOf=" + v1Published.minusNanos(1_000));
        assertThat(beforeV1.status().value()).isEqualTo(404);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(KnowledgeModuleConfiguration.class)
    static class TestApplication {

        @Bean
        JwtDecoder knowledgeTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @Bean
        AuthenticatedPrincipalResolver knowledgeTestPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }
    }
}
