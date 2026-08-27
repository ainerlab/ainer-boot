package {{package.name}};

import {{package.name}}.support.SecureTestConfiguration;
import dev.ainer.testsupport.postgres.AinerPostgresContainer;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(SecureTestConfiguration.class)
class {{application.className}}ApplicationSmokeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = AinerPostgresContainer.create();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataSource dataSource;

    @LocalServerPort
    private int port;

    @Test
    void healthIsPublicButApiFailsClosedWithoutToken() {
        RestTestClient client = RestTestClient.forLocalServer(restTemplate, port);
        RestResponse health = client.get("/actuator/health");
        assertThat(health.status().value()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        RestResponse ping = client.get("/api/ping");
        assertThat(ping.status().value()).isEqualTo(401);
        assertThat(ping.body()).contains("AINER.COMMON.UNAUTHENTICATED");
    }

    @Test
    void databaseAndProtectedOpenApiAreAvailable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("SELECT 1");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(SecureTestConfiguration.userToken(
                    "openapi-reader", "workspace.read"));
            return execution.execute(request, body);
        });
        RestResponse openApi = RestTestClient.forLocalServer(restTemplate, port).get("/v3/api-docs");
        assertThat(openApi.status().value()).isEqualTo(200);
        assertThat(openApi.body()).contains("\"openapi\"");
    }
}
