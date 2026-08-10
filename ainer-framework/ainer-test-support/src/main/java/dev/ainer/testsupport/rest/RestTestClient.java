package dev.ainer.testsupport.rest;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * JSON test client against a running {@code @SpringBootTest(RANDOM_PORT)} server. Wraps Boot's
 * {@link TestRestTemplate} so tests no longer repeat base-URL and JSON-header boilerplate.
 *
 * <pre>{@code
 * @Autowired private TestRestTemplate restTemplate;
 * @LocalServerPort private int port;
 *
 * RestTestClient client = RestTestClient.forLocalServer(restTemplate, port);
 * RestResponse response = client.postJson("/api/metricRows", """
 *         {"appId": "m1", "metric": "cpu"}
 *         """);
 * assertThat(response.status()).isEqualTo(201);
 * assertThat(response.jsonPath("$.id")).isNotEmpty();
 * }</pre>
 */
public record RestTestClient(TestRestTemplate restTemplate, String baseUrl) {

    public static RestTestClient forLocalServer(TestRestTemplate restTemplate, int port) {
        return new RestTestClient(restTemplate, "http://localhost:" + port);
    }

    public RestResponse get(String path) {
        return exchange(path, HttpMethod.GET, null);
    }

    public RestResponse postJson(String path, String jsonBody) {
        return exchange(path, HttpMethod.POST, jsonBody);
    }

    public RestResponse putJson(String path, String jsonBody) {
        return exchange(path, HttpMethod.PUT, jsonBody);
    }

    public RestResponse delete(String path) {
        return exchange(path, HttpMethod.DELETE, null);
    }

    public RestResponse exchange(String path, HttpMethod method, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(baseUrl + path, method, entity, String.class);
        return new RestResponse(response);
    }
}