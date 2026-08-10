package dev.ainer.testsupport.rest;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

/**
 * Wraps a raw {@link ResponseEntity} so tests can assert status and JSON path in one step.
 */
public record RestResponse(ResponseEntity<String> response) {

    public HttpStatusCode status() {
        return Objects.requireNonNull(response.getStatusCode());
    }

    public String header(String name) {
        return response.getHeaders().getFirst(name);
    }

    public String body() {
        String body = response.getBody();
        return body == null ? "" : body;
    }

    /**
     * Extracts a JSON path value from the response body using Jayway JsonPath.
     *
     * @throws com.jayway.jsonpath.PathNotFoundException when the path does not exist
     */
    public Object jsonPath(String path) {
        return JsonPath.read(body(), path);
    }
}