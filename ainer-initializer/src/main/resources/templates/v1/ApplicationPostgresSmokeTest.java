package {{package.name}};

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
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
    void pingReturnsPong() {
        RestResponse response = RestTestClient.forLocalServer(restTemplate, port).get("/api/ping");
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body()).contains("\"data\":\"pong\"");
        assertThat(response.header("X-Request-Id")).isNotBlank();
    }

    @Test
    void databaseIsReachable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            var result = statement.executeQuery("SELECT 1");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void actuatorHealthIsUp() {
        RestResponse response = RestTestClient.forLocalServer(restTemplate, port).get("/actuator/health");
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}