package dev.ainer.testsupport;

import dev.ainer.testsupport.application.TestSupportTestApplication;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestSupportTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TestSupportIntegrationTest {

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
    void restTestClientRunsFullJsonLifecycle() {
        RestTestClient client = RestTestClient.forLocalServer(restTemplate, port);

        RestResponse created = client.postJson("/api/probe", """
                {"name": "alpha"}
                """);
        assertThat(created.status().value()).isEqualTo(200);
        assertThat(created.jsonPath("$.status")).isEqualTo("created");
        long id = (long) ((Number) created.jsonPath("$.id")).longValue();

        RestResponse fetched = client.get("/api/probe/" + id);
        assertThat(fetched.status().value()).isEqualTo(200);
        assertThat(fetched.jsonPath("$.name")).isEqualTo("alpha");

        RestResponse updated = client.putJson("/api/probe/" + id, """
                {"name": "beta"}
                """);
        assertThat(updated.status().value()).isEqualTo(200);
        assertThat(updated.jsonPath("$.status")).isEqualTo("updated");

        RestResponse deleted = client.delete("/api/probe/" + id);
        assertThat(deleted.status().value()).isEqualTo(200);
        assertThat(deleted.jsonPath("$.status")).isEqualTo("deleted");
    }

    @Test
    void postgresDatasourceIsReachable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("SELECT 1");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}