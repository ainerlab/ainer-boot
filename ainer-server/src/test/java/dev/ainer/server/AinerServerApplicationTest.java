package dev.ainer.server;

import dev.ainer.web.request.RequestIds;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.workspace.enabled=false",
                "ainer.ai.enabled=false",
                "ainer.authorization.enabled=false",
                "ainer.dictionary.enabled=false",
                "ainer.config.enabled=false",
                "ainer.notification.enabled=false",
                "ainer.file.enabled=false",
                "ainer.organization.enabled=false",
                "ainer.ai.agents.enabled=false",
                "ainer.knowledge.enabled=false",
                "ainer.task.enabled=false",
                "ainer.security.resource-server.enabled=false",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
class AinerServerApplicationTest {

    @LocalServerPort
    private int port;

    @Test
    void startsAndExposesPlatformInformation() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/api/platform/info".formatted(port)))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestIds.HEADER)).isPresent();
        assertThat(response.body())
                .contains("\"code\":\"AINER.COMMON.OK\"")
                .contains("\"runtimeMode\":\"MONOLITH\"")
                .contains("\"javaFeatureVersion\":25");
    }

    @Test
    void disablingBusinessResourceServerDoesNotExposeMetrics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/actuator/prometheus".formatted(port)))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
        assertThat(response.body()).doesNotContain("jvm_").doesNotContain("process_");
    }
}
