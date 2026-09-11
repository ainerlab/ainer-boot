package dev.ainer.authorizationserver.identitycontrol;

import dev.ainer.authorizationserver.AinerAuthorizationServerApplication;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalMapper;
import dev.ainer.module.identity.foundation.ServicePrincipalRow;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份生命周期控制面的 HTTP 合同：受信 SERVICE 主体、最小 scope、失败关闭的状态机与非法的
 * 请求一律拒绝，成功路径写同事务审计。Authorization Server 发行物是唯一 HTTP adapter
 * （Identity 模块本身没有 Web 依赖），入口全部位于 {@code /internal/**} 且没有匿名可达路径。
 *
 * <p>与 {@code IdentitySecurityEpochRevocationIntegrationTest} 同源但职责不同：本测试只验证
 * 控制面的授权与错误合同，epoch 对 Token 的在线撤销效果在那边用真实 PKCE Token 证明。
 */
@Testcontainers
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "ainer.security.authorization-server.issuer=" + IdentityControlPlaneHttpTest.ISSUER,
                "ainer.security.authorization-server.audience=" + IdentityControlPlaneHttpTest.AUDIENCE,
                "ainer.security.authorization-server.signing-key.key-id=test-kid",
                "ainer.security.authorization-server.identity-control.enabled=true",
                "ainer.security.authorization-server.identity-control.trusted-service-id="
                        + IdentityControlPlaneHttpTest.OPERATOR_PRINCIPAL_ID
        })
class IdentityControlPlaneHttpTest {

    static final String ISSUER = "https://auth.ainer.test";
    static final String AUDIENCE = "ainer-api";
    static final String OPERATOR_PRINCIPAL_ID = "019c7200-0000-7000-8000-0000000000bb";
    private static final String OTHER_PRINCIPAL_ID = "019c7200-0000-7000-8000-0000000000cc";
    private static final String USERNAME = "control-plane-victim";
    private static final String PASSWORD = "control-plane-password-2026";
    private static final String ACCOUNT_SCOPE = "identity.accounts.manage";
    private static final String SERVICE_PRINCIPAL_SCOPE = "identity.service-principals.manage";

    private static final RSAKey SIGNING_KEY = JwtTestSupport.generateRsaKey();
    private static final Path KEY_DIRECTORY = createKeyDirectory();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_identity_control_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ainer.security.authorization-server.signing-key.private-key-location",
                () -> KEY_DIRECTORY.resolve("private.pem").toUri().toString());
        registry.add("ainer.security.authorization-server.signing-key.public-key-location",
                () -> KEY_DIRECTORY.resolve("public.pem").toUri().toString());
    }

    @LocalServerPort
    int port;

    @Autowired
    IdentityFoundationService foundationService;
    @Autowired
    ServicePrincipalFoundationService servicePrincipalFoundationService;
    @Autowired
    ServicePrincipalMapper servicePrincipalMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private int requestCounter;

    @BeforeEach
    void prepareFixtures() {
        jdbcTemplate.update("DELETE FROM ainer_identity_principal_lifecycle_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_credential");
        jdbcTemplate.update("DELETE FROM ainer_identity_login_identity");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_account");
        jdbcTemplate.update("DELETE FROM ainer_identity_oauth_client_binding");
        jdbcTemplate.update("DELETE FROM ainer_identity_service_principal");

        foundationService.registerHumanAccountWithPassword(
                new IdentityAuthorityRef(ISSUER), LoginIdentityType.USERNAME, ISSUER, USERNAME, PASSWORD);
        insertPrincipal(UUID.fromString(OPERATOR_PRINCIPAL_ID));
        insertPrincipal(UUID.fromString(OTHER_PRINCIPAL_ID));
    }

    @Test
    @DisplayName("状态机非法迁移与重复迁移都失败关闭，CLOSED 之后不可复活")
    void stateMachineFailsClosed() throws Exception {
        UUID accountId = accountId();

        assertThat(transition(accountId, "DISABLED", ACCOUNT_SCOPE).statusCode()).isEqualTo(200);
        // 重复禁用：不是静默 no-op，而是 409 且不再递增 epoch
        HttpResponse<String> repeated = transition(accountId, "DISABLED", ACCOUNT_SCOPE);
        assertThat(repeated.statusCode()).isEqualTo(409);
        assertThat(errorCode(repeated)).isEqualTo("AINER.IDENTITY.HUMAN_ACCOUNT_STATE_CONFLICT");
        assertThat(storedEpoch(accountId)).isEqualTo(1L);

        assertThat(transition(accountId, "ACTIVE", ACCOUNT_SCOPE).statusCode()).isEqualTo(200);
        assertThat(storedEpoch(accountId)).isEqualTo(2L);

        assertThat(transition(accountId, "CLOSED", ACCOUNT_SCOPE).statusCode()).isEqualTo(200);
        HttpResponse<String> resurrect = transition(accountId, "ACTIVE", ACCOUNT_SCOPE);
        assertThat(resurrect.statusCode()).isEqualTo(409);
        assertThat(errorCode(resurrect)).isEqualTo("AINER.IDENTITY.HUMAN_ACCOUNT_STATE_CONFLICT");
        assertThat(storedEpoch(accountId)).isEqualTo(3L);

        // 三次成功变更 = 三条审计，全部记录 epoch 恰好前进一格
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_principal_lifecycle_audit "
                        + "WHERE principal_id = ? AND new_security_epoch = previous_security_epoch + 1",
                Integer.class, accountId)).isEqualTo(3);
    }

    @Test
    @DisplayName("未知账号 404；非法请求体 400；不存在/过短凭据失败关闭")
    void invalidRequestsFailClosed() throws Exception {
        HttpResponse<String> unknown = transition(UUID.randomUUID(), "DISABLED", ACCOUNT_SCOPE);
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(errorCode(unknown)).isEqualTo("AINER.IDENTITY.HUMAN_ACCOUNT_NOT_FOUND");

        assertThat(post("/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DISABLED\"}", ACCOUNT_SCOPE).statusCode()).isEqualTo(400);
        assertThat(post("/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DELETED\", \"changeReference\": \"INC-1\"}", ACCOUNT_SCOPE)
                .statusCode()).isEqualTo(400);
        HttpResponse<String> shortPassword = post(
                "/internal/identity/accounts/" + accountId() + "/password-rotations",
                "{\"newPassword\": \"short\", \"changeReference\": \"INC-1\"}", ACCOUNT_SCOPE);
        assertThat(shortPassword.statusCode()).isEqualTo(400);
        HttpResponse<String> missingCredential = post(
                "/internal/identity/accounts/" + accountId() + "/credential-revocations",
                "{\"credentialType\": \"WEBAUTHN_PUBLIC_KEY\", \"changeReference\": \"INC-1\"}",
                ACCOUNT_SCOPE);
        assertThat(missingCredential.statusCode()).isEqualTo(404);
        assertThat(errorCode(missingCredential)).isEqualTo("AINER.IDENTITY.CREDENTIAL_NOT_FOUND");

        // 全部失败路径都没有产生状态变更或审计
        assertThat(storedStatus(accountId())).isEqualTo("ACTIVE");
        assertThat(storedEpoch(accountId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_principal_lifecycle_audit",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("人员 Token 与缺少 scope 的 SERVICE Token 都不能进入控制面")
    void humanTokenAndMissingScopeAreRejected() throws Exception {
        String userToken = JwtTestSupport.signUserJwt(
                SIGNING_KEY, ISSUER, AUDIENCE, accountId().toString(), ACCOUNT_SCOPE);
        HttpResponse<String> asUser = post(
                "/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-1\"}", userToken);
        assertThat(asUser.statusCode()).isEqualTo(403);

        String wrongScope = JwtTestSupport.signServiceJwt(
                SIGNING_KEY, ISSUER, AUDIENCE, OPERATOR_PRINCIPAL_ID, "oauth.browser-clients.manage");
        assertThat(post("/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-1\"}", wrongScope)
                .statusCode()).isEqualTo(403);

        // 服务主体端点使用独立 scope：账号 scope 不能提权到服务主体
        assertThat(post("/internal/identity/service-principals/" + OTHER_PRINCIPAL_ID
                        + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-1\"}", ACCOUNT_SCOPE)
                .statusCode()).isEqualTo(403);
        assertThat(post("/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-1\"}", SERVICE_PRINCIPAL_SCOPE)
                .statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("服务主体禁用递增 epoch，且被禁用/轮换后的调用方 Token 立即失去控制面权限")
    void servicePrincipalDisableBumpsEpochAndRevokesCallerAccess() throws Exception {
        HttpResponse<String> disableOperator = post(
                "/internal/identity/service-principals/" + OPERATOR_PRINCIPAL_ID + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-2026-778\"}",
                SERVICE_PRINCIPAL_SCOPE);
        assertThat(disableOperator.statusCode()).isEqualTo(200);
        assertThat(disableOperator.body())
                .contains("\"previousSecurityEpoch\":0")
                .contains("\"securityEpoch\":1");

        Map<String, Object> principal = jdbcTemplate.queryForMap(
                "SELECT status, security_epoch FROM ainer_identity_service_principal WHERE id = ?",
                UUID.fromString(OPERATOR_PRINCIPAL_ID));
        assertThat(principal.get("status")).isEqualTo("DISABLED");
        assertThat(principal.get("security_epoch")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operation FROM ainer_identity_principal_lifecycle_audit "
                        + "WHERE principal_type = 'SERVICE_PRINCIPAL'",
                String.class)).isEqualTo("DISABLED");

        // 旧 Token 仍带 sec_epoch=0，而主体当前 epoch=1：控制面立即拒绝（不等到 Token 过期）
        HttpResponse<String> withStaleToken = transition(accountId(), "DISABLED", ACCOUNT_SCOPE);
        assertThat(withStaleToken.statusCode()).isEqualTo(403);
        assertThat(storedEpoch(accountId())).isZero();
    }

    @Test
    @DisplayName("密码轮换只返回 epoch 投影，绝不出现在响应或审计里的密码材料")
    void passwordRotationNeverLeaksCredentialMaterial() throws Exception {
        String newPassword = "rotated-control-plane-2026";
        HttpResponse<String> response = post(
                "/internal/identity/accounts/" + accountId() + "/password-rotations",
                "{\"newPassword\": \"" + newPassword + "\", \"changeReference\": \"INC-2026-777\"}",
                ACCOUNT_SCOPE);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .doesNotContain(newPassword, "bcrypt", "{bcrypt}")
                .contains("\"previousSecurityEpoch\":0")
                .contains("\"securityEpoch\":1");
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT credential_data FROM ainer_identity_credential WHERE account_id = ? "
                        + "AND status = 'ACTIVE'",
                String.class, accountId());
        assertThat(storedHash).isNotEqualTo(newPassword).startsWith("{bcrypt}");
        String auditRow = jdbcTemplate.queryForObject(
                "SELECT change_reference || '|' || operation || '|' || credential_type "
                        + "FROM ainer_identity_principal_lifecycle_audit",
                String.class);
        assertThat(auditRow).isEqualTo("INC-2026-777|PASSWORD_ROTATED|PASSWORD");
    }

    @Test
    @DisplayName("控制面没有匿名入口：无 Token 请求在到达 Controller 之前被拦下")
    void controlPlanePathIsNotAnonymous() throws Exception {
        // 路径归 /internal/** 过滤链管：未带 Token 的请求必须在到达 Controller 之前被 401 拦下。
        HttpResponse<String> anonymous = post(
                "/internal/identity/accounts/" + accountId() + "/status-transitions",
                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-1\"}", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> transition(UUID principalId, String status, String scope)
            throws Exception {
        return post("/internal/identity/accounts/" + principalId + "/status-transitions",
                "{\"status\": \"" + status + "\", \"changeReference\": \"INC-2026-777\"}", scope);
    }

    private HttpResponse<String> post(String path, String body, String scope) throws Exception {
        requestCounter++;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Request-Id", "control-plane-test-" + requestCounter)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (scope != null) {
            builder.header("Authorization", "Bearer " + JwtTestSupport.signServiceJwt(
                    SIGNING_KEY, ISSUER, AUDIENCE, OPERATOR_PRINCIPAL_ID, scope));
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String errorCode(HttpResponse<String> response) throws Exception {
        Object code = JSON.readValue(response.body(), JSON_MAP).get("code");
        return code == null ? null : code.toString();
    }

    private UUID accountId() {
        UUID accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM ainer_identity_login_identity "
                        + "WHERE normalized_identifier = ? AND provider_authority = ?",
                UUID.class, USERNAME, ISSUER);
        assertThat(accountId).isNotNull();
        return accountId;
    }

    private long storedEpoch(UUID accountId) {
        Long epoch = jdbcTemplate.queryForObject(
                "SELECT security_epoch FROM ainer_identity_human_account WHERE id = ?",
                Long.class, accountId);
        return epoch == null ? -1L : epoch;
    }

    private String storedStatus(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_human_account WHERE id = ?",
                String.class, accountId);
    }

    private void insertPrincipal(UUID principalId) {
        ServicePrincipalRow row = new ServicePrincipalRow();
        row.setId(principalId);
        row.setIssuer(ISSUER);
        row.setStatus("ACTIVE");
        row.setSecurityEpoch(0L);
        row.setCreatedAt(Instant.now());
        servicePrincipalMapper.insertPrincipal(row);
    }

    private static Path createKeyDirectory() {
        try {
            Path directory = Files.createTempDirectory("ainer-identity-control-keys");
            writePem(directory.resolve("private.pem"), "PRIVATE KEY",
                    SIGNING_KEY.toRSAPrivateKey().getEncoded());
            writePem(directory.resolve("public.pem"), "PUBLIC KEY",
                    SIGNING_KEY.toRSAPublicKey().getEncoded());
            return directory;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create control-plane test signing keys", exception);
        }
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        Files.writeString(path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }
}
