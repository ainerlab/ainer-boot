package dev.ainer.module.file;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.testsupport.rest.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JWT HTTP test for the file management API (ADR-0040). The JWT is signed with a test RSA key
 * and verified by a real {@link JwtDecoder} through the resource-server security chain; the
 * production {@code AuthenticatedPrincipalResolver} resolves the typed principal. Upload limits
 * are asserted with real transport statuses (413/415), not 200-wrapped errors.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = FileStorageHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.file.enabled=true",
                "ainer.file.max-size-bytes=128",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class FileStorageHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_file_http_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ainer.storage.local.base-directory", () -> storageDir.toString());
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_file_audit");
        jdbcTemplate.execute("DELETE FROM ainer_file_object");
        authenticateWith(signUserJwt("account:1", "file.read file.write"));
    }

    // --------------------------------------------------------------- tests

    @Test
    void pageWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());

        ResponseEntity<String> response = restTemplate.getForEntity(uri("/api/files"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void requestWithoutScopeIsForbidden() {
        // Explicit Authorization header carrying a token without file.read; the default-token
        // interceptor defers to an explicit header (see bearerInterceptor).
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(signUserJwt("account:1", "other.read"));

        ResponseEntity<String> response = restTemplate.exchange(
                uri("/api/files"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void uploadDownloadAndDeleteRoundTrip() {
        byte[] bytes = "http round trip".getBytes(StandardCharsets.UTF_8);
        RestResponse upload = upload("docs", "report.txt", "text/plain", bytes);

        assertThat(upload.status().value()).isEqualTo(201);
        assertThat(upload.jsonPath("$.code")).isEqualTo("AINER.COMMON.OK");
        String id = (String) upload.jsonPath("$.data.id");
        assertThat((String) upload.jsonPath("$.data.checksumSha256")).hasSize(64);

        ResponseEntity<byte[]> download = restTemplate.getForEntity(
                URI.create("/api/files/" + id + "/content"), byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getBody()).isEqualTo(bytes);
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.txt");

        RestResponse page = new RestResponse(
                restTemplate.getForEntity(uri("/api/files?namespace=docs"), String.class));
        assertThat(page.status().value()).isEqualTo(200);
        assertThat(page.jsonPath("$.data.total")).isEqualTo(1);

        RestResponse delete = new RestResponse(restTemplate.exchange(
                URI.create("/api/files/" + id), HttpMethod.DELETE, null, String.class));
        assertThat(delete.status().value()).isEqualTo(200);

        ResponseEntity<byte[]> missing = restTemplate.getForEntity(
                URI.create("/api/files/" + id + "/content"), byte[].class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void auditRowsArePersistedForUploadAndDelete() {
        RestResponse upload = upload("docs", "a.txt", "text/plain", "audit".getBytes());
        assertThat(upload.status().value()).isEqualTo(201);
        String id = (String) upload.jsonPath("$.data.id");

        restTemplate.exchange(URI.create("/api/files/" + id), HttpMethod.DELETE, null, String.class);

        Integer uploaded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_audit WHERE operation = 'UPLOADED'", Integer.class);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_audit WHERE operation = 'DELETED'", Integer.class);
        assertThat(uploaded).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void disallowedContentTypeIsRejectedWithRealStatus() {
        RestResponse response = upload("docs", "tool.exe",
                "application/x-msdownload", "MZ".getBytes());

        assertThat(response.status().value()).isEqualTo(415);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.FILE.CONTENT_TYPE_NOT_ALLOWED");
    }

    @Test
    void oversizeUploadIsRejectedWithRealStatus() {
        byte[] oversize = new byte[200]; // limit is 128 in this test context

        RestResponse response = upload("docs", "big.json", "application/json", oversize);

        assertThat(response.status().value()).isEqualTo(413);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.FILE.FILE_TOO_LARGE");
    }

    // --------------------------------------------------------------- helpers

    /**
     * Absolute URL. TestRestTemplate routes relative URLs through a separate template handler that
     * does not observe later interceptor edits, so scope-switching tests must use absolute URLs
     * (same conclusion as the authorization HTTP test).
     */
    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Multipart upload with a typed file part so {@code MultipartFile.getContentType()} is set. */
    private RestResponse upload(String namespace, String filename,
            String contentType, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders);

        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("namespace", namespace);
        body.add("file", filePart);

        ResponseEntity<String> response = restTemplate.postForEntity(
                uri("/api/files"), new HttpEntity<>(body, headers), String.class);
        return new RestResponse(response);
    }

    private ClientHttpRequestInterceptor bearerInterceptor(String jwt) {
        // Only supply the default token when the request carries no Authorization header, so an
        // explicit per-test header (e.g. a scope-restricted token) always wins over the default.
        return (request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        };
    }

    private void authenticateWith(String jwt) {
        // setInterceptors (not mutating the returned list): RestTemplate only rebuilds its
        // intercepting request factory on the setter, so plain clear()/add() edits stay invisible.
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of(bearerInterceptor(jwt)));
    }

    private static String signUserJwt(String subjectId, String scope) {
        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER)
                            .audience(AUDIENCE)
                            .subject(subjectId)
                            .claim("token_profile", "USER_NEUTRAL_V1")
                            .claim("claim_contract_version", "1")
                            .claim("actor_type", "USER")
                            .claim("scope", scope)
                            .claim("amr", "pwd")
                            .claim("client_id", "test-client")
                            .claim("sec_epoch", 0L)
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                            .build());
            signedJWT.sign(new RSASSASigner(RSA_JWK.toRSAPrivateKey()));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign test JWT", exception);
        }
    }

    private static RSAKey generateRsaKey() {
        try {
            return new RSAKeyGenerator(3072).keyID("test-kid").generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate test RSA key", exception);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(FileModuleConfiguration.class)
    static class TestApplication {

        /**
         * @Primary resolver (same pattern as the authorization HTTP test): reads the verified
         * {@code Jwt} from the SecurityContext and resolves it through
         * {@link dev.ainer.security.token.ReferenceTokenProfileResolver}. Primary so it wins over
         * any resolver leaking from other tests' component-scan scope in the same module.
         */
        @Bean
        @org.springframework.context.annotation.Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver jwtPrincipalResolver() {
            dev.ainer.security.token.ReferenceTokenProfileResolver profileResolver =
                    new dev.ainer.security.token.ReferenceTokenProfileResolver();
            return () -> {
                var authentication = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                        || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                    throw new dev.ainer.core.error.BusinessException(
                            dev.ainer.core.error.StandardErrorCode.UNAUTHENTICATED);
                }
                if (!(authentication.getPrincipal()
                        instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
                    throw new dev.ainer.core.error.BusinessException(
                            dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
                }
                return profileResolver.resolve(new dev.ainer.security.token.VerifiedJwtClaims(
                        jwt.getIssuer().toString(),
                        jwt.getSubject(),
                        new java.util.LinkedHashSet<>(jwt.getAudience()),
                        jwt.getExpiresAt(),
                        jwt.getClaims()));
            };
        }

        @Bean
        JwtDecoder testJwtDecoder() throws Exception {
            RSAPublicKey publicKey = RSA_JWK.toRSAPublicKey();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(ISSUER);
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains(AUDIENCE)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required audience is missing", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
            return decoder;
        }
    }
}
