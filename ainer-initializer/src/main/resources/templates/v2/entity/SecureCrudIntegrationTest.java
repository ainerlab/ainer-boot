package {{package.name}}.{{entity.package}};

import {{package.name}}.support.SecureTestConfiguration;
import com.jayway.jsonpath.JsonPath;
import dev.ainer.testsupport.postgres.AinerPostgresContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(SecureTestConfiguration.class)
class {{entity.className}}SecureCrudIntegrationTest {

    private static final String OWNER = "{{resource.path}}-owner";
    private static final String OUTSIDER = "{{resource.path}}-outsider";
    private static final String ALL_SCOPES =
            "workspace.read workspace.write {{entity.scope.read}} {{entity.scope.write}}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = AinerPostgresContainer.create();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @BeforeEach
    void cleanAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM {{audit.table.name}}");
        jdbcTemplate.execute("DELETE FROM {{table.name}}");
        jdbcTemplate.execute("DELETE FROM ainer_workspace_authorization_audit");
        jdbcTemplate.execute("DELETE FROM ainer_workspace_member");
        jdbcTemplate.execute("DELETE FROM ainer_workspace");
        authenticate(OWNER, ALL_SCOPES);
    }

    @Test
    void secureLifecycleEnforcesWorkspaceScopePaginationAndOptimisticLock() {
        UUID firstWorkspace = createWorkspace("first");
        UUID secondWorkspace = createWorkspace("second");
        String base = resourceBase(firstWorkspace);

        ResponseEntity<String> created = exchange(
                base, HttpMethod.POST, {{test.createdPayload}});
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        String id = JsonPath.parse(created.getBody()).read("$.data.id", String.class);
        assertThat(id).isNotBlank();
        assertThat(created.getBody()).contains("{{test.firstCreatedValue}}");
        assertThat(created.getBody()).contains("\"version\":0");

        ResponseEntity<String> updated = exchange(
                base + "/" + id, HttpMethod.PUT, {{test.updatedPayload}});
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody()).contains("{{test.firstUpdatedValue}}");
        assertThat(updated.getBody()).contains("\"version\":1");

        ResponseEntity<String> stale = exchange(
                base + "/" + id, HttpMethod.PUT, {{test.updatedPayload}});
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(stale.getBody()).contains(
                "{{project.errorNamespace}}.{{entity.errorSegment}}.CONCURRENT_MODIFICATION");

        ResponseEntity<String> invalidPage = exchange(
                base + "?size=101", HttpMethod.GET, null);
        assertThat(invalidPage.getStatusCode().value()).isEqualTo(400);
        assertThat(invalidPage.getBody()).contains(
                "{{project.errorNamespace}}.{{entity.errorSegment}}.INVALID_PAGE");

        ResponseEntity<String> crossWorkspace = exchange(
                resourceBase(secondWorkspace) + "/" + id, HttpMethod.GET, null);
        assertThat(crossWorkspace.getStatusCode().value()).isEqualTo(404);
        assertThat(crossWorkspace.getBody()).contains(
                "{{project.errorNamespace}}.{{entity.errorSegment}}.NOT_FOUND");

        authenticate(OUTSIDER, "workspace.read {{entity.scope.read}}");
        ResponseEntity<String> outsider = exchange(base + "/" + id, HttpMethod.GET, null);
        assertThat(outsider.getStatusCode().value()).isEqualTo(404);
        assertThat(outsider.getBody()).contains("AINER.WORKSPACE.NOT_FOUND");

        authenticate(OWNER, "workspace.read");
        ResponseEntity<String> missingProductScope = exchange(
                base, HttpMethod.POST, {{test.createdPayload}});
        assertThat(missingProductScope.getStatusCode().value()).isEqualTo(403);
        assertThat(missingProductScope.getBody()).contains(
                "{{project.errorNamespace}}.{{entity.errorSegment}}.ACCESS_DENIED");
        Integer deniedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM {{audit.table.name}} WHERE decision = 'DENY'",
                Integer.class);
        assertThat(deniedAudits).isGreaterThanOrEqualTo(1);

        authenticate(OWNER, ALL_SCOPES);
        ResponseEntity<String> deleted = exchange(
                base + "/" + id + "?version=1", HttpMethod.DELETE, null);
        assertThat(deleted.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> afterDelete = exchange(base + "/" + id, HttpMethod.GET, null);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);

        restTemplate.getRestTemplate().getInterceptors().clear();
        ResponseEntity<String> unauthenticated = exchange(base, HttpMethod.GET, null);
        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthenticated.getBody()).contains("AINER.COMMON.UNAUTHENTICATED");
    }

    private UUID createWorkspace(String suffix) {
        ResponseEntity<String> response = exchange(
                "/api/workspaces", HttpMethod.POST,
                "{\"name\":\"{{entity.className}} " + suffix + "\"}");
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(JsonPath.parse(response.getBody()).read("$.data.id", String.class));
    }

    private String resourceBase(UUID workspaceId) {
        return "/api/workspaces/" + workspaceId + "/{{resource.path}}";
    }

    private void authenticate(String subjectId, String scopes) {
        String token = SecureTestConfiguration.userToken(subjectId, scopes);
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        });
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(
                "http://localhost:" + port + path,
                method,
                new HttpEntity<>(body, headers),
                String.class);
    }
}
