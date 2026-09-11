package dev.ainer.web.error;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 状态语义保真度测试（真实 Tomcat + 真实 HTTP，不用 mock）。
 *
 * <p>覆盖 Spring MVC 判定的整族异常：405/415/非法请求体/406 必须保留真实状态码并包成
 * {@link ApiResponse} 信封，不能被 500 兜底吞掉；429/503 必须映射到对应稳定错误码而不是
 * 被压成 400/500；同时回归 400/404/409/422 与成功信封不变。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.banner-mode=off")
@AutoConfigureTestRestTemplate
class GlobalExceptionHandlerHttpStatusTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 405 / 415 / 406

    @Test
    void methodNotAllowedKeepsRealStatusAndStableErrorCode() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/probe/post-only"), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        // RFC 9110 要求 405 携带 Allow；Spring 判定的响应头必须原样保留
        assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).contains(HttpMethod.POST.name());
        assertThat(response.getHeaders().getFirst(RequestIds.HEADER)).isNotBlank();

        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.METHOD_NOT_ALLOWED.code());
        assertThat(body.path("requestId").asText())
                .isEqualTo(response.getHeaders().getFirst(RequestIds.HEADER));
        assertThat(body.path("data").isNull()).isTrue();
    }

    @Test
    void unsupportedContentTypeKeepsRealStatus() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/probe/json-only"), HttpMethod.POST, new HttpEntity<>("{\"value\":\"a\"}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        // Spring 判定的可接受媒体类型必须原样保留
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.UNSUPPORTED_MEDIA_TYPE.code());
        assertThat(body.path("requestId").asText()).isNotBlank();
    }

    @Test
    void malformedJsonBodyIsBadRequestNotServerError() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/probe/json-only"), HttpMethod.POST, new HttpEntity<>("{\"value\": ", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.INVALID_REQUEST.code());
        assertThat(body.path("requestId").asText()).isNotBlank();
    }

    @Test
    void notAcceptableKeepsRealStatus() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/probe/json-only"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.NOT_ACCEPTABLE.code());
        assertThat(body.path("requestId").asText()).isNotBlank();
        // Accept 排除 JSON 时，若不显式声明响应类型，内容协商会丢弃错误信封（body 为空）
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }

    // ---------------------------------------------------------------- 429 / 503 状态码映射

    @Test
    void tooManyRequestsMapsToRateLimitedCode() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/probe/rate-limited"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.RATE_LIMITED.code());
        assertThat(body.path("message").asText()).isEqualTo("登录尝试过于频繁");
    }

    @Test
    void serviceUnavailableMapsToServiceUnavailableCode() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/probe/unavailable"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.SERVICE_UNAVAILABLE.code());
        assertThat(body.path("requestId").asText()).isNotBlank();
    }

    // ---------------------------------------------------------------- 既有语义回归

    @Test
    void bindingAndValidationFailuresStayBadRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/probe/json-only"), HttpMethod.POST, new HttpEntity<>("{\"value\":\"  \"}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.INVALID_REQUEST.code());
        assertThat(body.path("message").asText()).startsWith("value: ");

        ResponseEntity<String> missingHeader =
                restTemplate.getForEntity(url("/probe/required-header"), String.class);
        assertThat(missingHeader.getStatusCode().value()).isEqualTo(400);
        assertThat(body(missingHeader).path("code").asText())
                .isEqualTo(StandardErrorCode.INVALID_REQUEST.code());
    }

    @Test
    void unknownResourceStaysNotFound() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/probe/absent"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.NOT_FOUND.code());
        assertThat(body.path("requestId").asText()).isNotBlank();
    }

    @Test
    void frameworkConflictAndBusinessRuleViolationKeepTheirStatus() throws Exception {
        ResponseEntity<String> conflict = restTemplate.getForEntity(url("/probe/conflict"), String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(body(conflict).path("code").asText()).isEqualTo(StandardErrorCode.CONFLICT.code());

        ResponseEntity<String> businessRule =
                restTemplate.getForEntity(url("/probe/business-rule"), String.class);
        assertThat(businessRule.getStatusCode().value()).isEqualTo(422);
        JsonNode businessBody = body(businessRule);
        assertThat(businessBody.path("code").asText())
                .isEqualTo(StandardErrorCode.BUSINESS_RULE_VIOLATION.code());
        assertThat(businessBody.path("message").asText()).isEqualTo("订单状态不允许取消");
    }

    @Test
    void successEnvelopeIsUnchanged() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/probe/success"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.path("code").asText()).isEqualTo(StandardErrorCode.OK.code());
        assertThat(body.path("message").asText()).isEqualTo(StandardErrorCode.OK.defaultMessage());
        assertThat(body.path("data").asText()).isEqualTo("ok");
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(body.path("requestId").asText())
                .isEqualTo(response.getHeaders().getFirst(RequestIds.HEADER));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    record ProbePayload(@NotBlank String value) {
    }

    @RestController
    static class ProbeController {

        /** 只支持 POST：用于 405。 */
        @PostMapping("/probe/post-only")
        ApiResponse<String> postOnly(@RequestBody ProbePayload payload, HttpServletRequest request) {
            return ApiResponse.success(payload.value(), RequestIds.currentOrCreate(request));
        }

        /** 只产出 JSON：用于 415 与 406。 */
        @GetMapping(path = "/probe/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<String> jsonOnly(HttpServletRequest request) {
            return ApiResponse.success("accepted", RequestIds.currentOrCreate(request));
        }

        @PostMapping(path = "/probe/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<String> jsonOnlyBody(
                @Valid @RequestBody ProbePayload payload, HttpServletRequest request) {
            return ApiResponse.success(payload.value(), RequestIds.currentOrCreate(request));
        }

        @GetMapping("/probe/success")
        ApiResponse<String> success(HttpServletRequest request) {
            return ApiResponse.success("ok", RequestIds.currentOrCreate(request));
        }

        @GetMapping("/probe/required-header")
        void requiredHeader(@RequestHeader("Idempotency-Key") String idempotencyKey) {
        }

        @GetMapping("/probe/business-rule")
        void businessRule() {
            throw new BusinessException(StandardErrorCode.BUSINESS_RULE_VIOLATION, "订单状态不允许取消");
        }

        @GetMapping("/probe/rate-limited")
        void rateLimited() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁");
        }

        @GetMapping("/probe/unavailable")
        void unavailable() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "依赖服务不可用");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "状态冲突");
        }
    }
}
