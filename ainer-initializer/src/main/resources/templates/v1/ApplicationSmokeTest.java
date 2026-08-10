package {{package.name}};

import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class {{application.className}}ApplicationSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
    void actuatorHealthIsUp() {
        RestResponse response = RestTestClient.forLocalServer(restTemplate, port).get("/actuator/health");
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}