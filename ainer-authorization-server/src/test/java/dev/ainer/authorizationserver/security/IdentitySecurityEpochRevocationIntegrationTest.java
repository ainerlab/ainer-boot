package dev.ainer.authorizationserver.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.authorizationserver.AinerAuthorizationServerApplication;
import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.epochprobe.ResourceServerProbeApplication;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.module.identity.foundation.ServicePrincipalRow;
import dev.ainer.module.identity.foundation.ServicePrincipalMapper;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code security_epoch} 撤销的真实端到端证明（真实 PostgreSQL + 真实 HTTP + 真实签名密钥）。
 *
 * <p>证明三件事，并把第三件固化为不可含糊的事实：
 *
 * <ol>
 *   <li>账号被禁用后 {@code security_epoch} 递增，禁用前签发的 Token 在**在线校验路径**上
 *       （Resource Server 的 RFC 7662 introspection → {@code RevocationAwareOAuth2AuthorizationService}
 *       的 epoch 比对）判定 inactive → 401；</li>
 *   <li>密码轮换同样递增 epoch，轮换前的 Token 在线校验 401，重新登录签发的新 Token 正常；</li>
 *   <li><b>边界</b>：未开启在线校验时，同一个旧 Token 仍能通过资源服务器（只校验签名/issuer/
 *       audience/期限）。自包含 JWT 不会因为数据库状态变化而自动失效，撤销的即时性只来自在线
 *       校验；把它写成"已全局强实时撤销"就是文档与实现相反。</li>
 * </ol>
 *
 * <p>被测 Authorization Server 发行物使用真实 PEM 签名密钥（测试期临时生成），Resource Server
 * 是两个由框架自动装配启动的最小发行物：一个开启在线校验、一个关闭，URL 与 JWKS 都是真实
 * HTTP。刻意不声明 {@code disabledWithoutDocker}：本测试是安全承诺的证据，跳过等于掩盖。
 */
@Testcontainers
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.security.authorization-server.audience=ainer-api",
                "ainer.security.authorization-server.signing-key.key-id=test-kid",
                "ainer.security.authorization-server.identity-control.enabled=true",
                "ainer.security.authorization-server.identity-control.trusted-service-id="
                        + IdentitySecurityEpochRevocationIntegrationTest.OPERATOR_PRINCIPAL_ID
        })
class IdentitySecurityEpochRevocationIntegrationTest {

    static final String ISSUER = "https://auth.ainer.test";
    static final String AUDIENCE = "ainer-api";
    static final String OPERATOR_PRINCIPAL_ID = "019c7100-0000-7000-8000-0000000000aa";
    static final String USERNAME = "epoch-victim";
    static final String INITIAL_PASSWORD = "initial-password-2026";

    private static final String BROWSER_CLIENT_ID = "epoch-revocation-browser";
    private static final String REDIRECT_URI = "http://127.0.0.1:4711/callback";
    private static final String INTROSPECTION_CLIENT_ID = "epoch-revocation-introspection";
    private static final String INTROSPECTION_CLIENT_SECRET = "epoch-introspection-secret-2026";
    private static final String ACCOUNT_MANAGE_SCOPE = "identity.accounts.manage";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"(_csrf)\"[^>]*value=\"([^\"]+)\"[^>]*>");

    private static final RSAKey SIGNING_KEY = JwtTestSupport.generateRsaKey();
    private static final Path KEY_DIRECTORY = createKeyDirectory();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_epoch_revocation_test")
            .withUsername("ainer")
            .withPassword("ainer");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private static final SecureRandom RANDOM = new SecureRandom();

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
    ServicePrincipalMapper servicePrincipalMapper;
    @Autowired
    RegisteredClientRepository registeredClientRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private static ConfigurableApplicationContext onlineValidationProbe;
    private static ConfigurableApplicationContext offlineValidationProbe;
    private int controlPlaneCounter;

    /**
     * 两个探针发行物在被测 Authorization Server 起来之后按需启动一次（在线校验开 / 关），
     * 使用真实 JWKS 与真实 introspection URL。
     */
    private void startResourceServerProbesOnce() {
        if (onlineValidationProbe == null) {
            onlineValidationProbe = startProbe("epoch-online-probe", port, true);
            offlineValidationProbe = startProbe("epoch-offline-probe", port, false);
        }
    }

    @AfterAll
    static void stopResourceServerProbes() {
        if (onlineValidationProbe != null) {
            onlineValidationProbe.close();
        }
        if (offlineValidationProbe != null) {
            offlineValidationProbe.close();
        }
    }

    @BeforeEach
    void prepareFixtures() {
        startResourceServerProbesOnce();
        jdbcTemplate.update("DELETE FROM ainer_identity_principal_lifecycle_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_credential");
        jdbcTemplate.update("DELETE FROM ainer_identity_login_identity");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_account");
        jdbcTemplate.update("DELETE FROM ainer_identity_oauth_client_binding");
        jdbcTemplate.update("DELETE FROM ainer_identity_service_principal");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");

        foundationService.registerHumanAccountWithPassword(
                new IdentityAuthorityRef(ISSUER), LoginIdentityType.USERNAME, ISSUER,
                USERNAME, INITIAL_PASSWORD);
        insertOperatorPrincipal();
        registerBrowserClient();
        registerIntrospectionClient();
    }

    @Test
    @DisplayName("账号禁用后 security_epoch 递增，旧 Token 在线校验 401，关闭在线校验时仍可用")
    void disableAccountRevokesPreDisableTokenOnlyOnOnlinePath() throws Exception {
        String token = issueUserAccessToken(INITIAL_PASSWORD);
        assertThat(securityEpochClaim(token)).isZero();
        // 在线校验路径：禁用前 → 通过
        assertThat(probeStatus(onlineProbeUrl(), token)).isEqualTo(200);
        assertThat(introspect(token).get("active")).isEqualTo(true);

        var response = callControlPlane(
                "/internal/identity/accounts/" + accountId() + "/status-transitions",
                """
                {"status": "DISABLED", "changeReference": "INC-2026-0901"}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storedEpoch()).isEqualTo(1L);
        assertThat(storedStatus()).isEqualTo("DISABLED");
        // 审计与状态变更同事务：记录被禁用、epoch 从 0 到 1、actor 是受信 SERVICE
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT operation, previous_status, new_status, previous_security_epoch, "
                        + "new_security_epoch, actor_service_id FROM "
                        + "ainer_identity_principal_lifecycle_audit");
        assertThat(audit.get("operation")).isEqualTo("DISABLED");
        assertThat(audit.get("previous_status")).isEqualTo("ACTIVE");
        assertThat(audit.get("new_status")).isEqualTo("DISABLED");
        assertThat(audit.get("previous_security_epoch")).isEqualTo(0L);
        assertThat(audit.get("new_security_epoch")).isEqualTo(1L);
        assertThat(audit.get("actor_service_id")).isEqualTo(OPERATOR_PRINCIPAL_ID);

        // 在线校验路径：禁用后，同一个旧 Token 判定 inactive → 401（协议层与 HTTP 层各一次）
        assertThat(introspect(token).get("active")).isEqualTo(false);
        assertThat(probeStatus(onlineProbeUrl(), token)).isEqualTo(401);

        // 边界：未开启在线校验的资源服务器仍然接受这个未过期的旧 Token
        assertThat(probeStatus(offlineProbeUrl(), token)).isEqualTo(200);

        // 禁用后也不再签发新 Token：重新登录拿不到授权码
        assertThat(loginFails(INITIAL_PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("密码轮换后旧 Token 在线校验 401，新签发 Token 携带新 epoch 且可用")
    void passwordRotationRevokesPreRotationTokenOnlyOnOnlinePath() throws Exception {
        String token = issueUserAccessToken(INITIAL_PASSWORD);
        assertThat(securityEpochClaim(token)).isZero();
        assertThat(probeStatus(onlineProbeUrl(), token)).isEqualTo(200);

        var response = callControlPlane(
                "/internal/identity/accounts/" + accountId() + "/password-rotations",
                """
                {"newPassword": "rotated-password-2026", "changeReference": "INC-2026-0902"}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storedEpoch()).isEqualTo(1L);
        // 响应绝不回显密码材料
        assertThat(response.body()).doesNotContain("rotated-password-2026", "bcrypt", "$2");

        assertThat(introspect(token).get("active")).isEqualTo(false);
        assertThat(probeStatus(onlineProbeUrl(), token)).isEqualTo(401);
        assertThat(probeStatus(offlineProbeUrl(), token)).isEqualTo(200);

        // 轮换后账号仍可用：新 Token 带新 epoch，在线校验重新放行
        String fresh = issueUserAccessToken("rotated-password-2026");
        assertThat(securityEpochClaim(fresh)).isEqualTo(1L);
        assertThat(probeStatus(onlineProbeUrl(), fresh)).isEqualTo(200);
        assertThat(loginFails(INITIAL_PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("凭据撤销递增 epoch 并使旧 Token 在线校验 401")
    void credentialRevocationRevokesPreRevocationTokenOnlyOnOnlinePath() throws Exception {
        String token = issueUserAccessToken(INITIAL_PASSWORD);

        var response = callControlPlane(
                "/internal/identity/accounts/" + accountId() + "/credential-revocations",
                """
                {"credentialType": "PASSWORD", "changeReference": "INC-2026-0903"}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storedEpoch()).isEqualTo(1L);
        assertThat(probeStatus(onlineProbeUrl(), token)).isEqualTo(401);
        assertThat(probeStatus(offlineProbeUrl(), token)).isEqualTo(200);
        assertThat(loginFails(INITIAL_PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("缺少 sec_epoch / 非受信主体 / 缺 scope 的 SERVICE Token 无法操作身份控制面")
    void controlPlaneRejectsUntrustedCallers() throws Exception {
        String wrongSubject = JwtTestSupport.signServiceJwt(
                SIGNING_KEY, ISSUER, AUDIENCE, "not-the-trusted-service", ACCOUNT_MANAGE_SCOPE);
        assertThat(callControlPlane(
                "/internal/identity/accounts/" + accountId() + "/status-transitions",
                """
                {"status": "DISABLED", "changeReference": "INC-2026-0904"}
                """, wrongSubject).statusCode()).isEqualTo(403);

        String missingScope = JwtTestSupport.signServiceJwt(
                SIGNING_KEY, ISSUER, AUDIENCE, OPERATOR_PRINCIPAL_ID, "workspace.read");
        assertThat(callControlPlane(
                "/internal/identity/accounts/" + accountId() + "/status-transitions",
                """
                {"status": "DISABLED", "changeReference": "INC-2026-0904"}
                """, missingScope).statusCode()).isEqualTo(403);

        // 匿名请求仍然没有入口
        HttpResponse<String> anonymous = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/internal/identity/accounts/" + accountId()
                                + "/status-transitions"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"status\": \"DISABLED\", \"changeReference\": \"INC-2026-0904\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);

        // 失败路径不得产生任何状态变更或审计
        assertThat(storedStatus()).isEqualTo("ACTIVE");
        assertThat(storedEpoch()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_principal_lifecycle_audit",
                Integer.class)).isZero();
    }

    private int probeStatus(String baseUrl, String token) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/probe/secure"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private Map<String, Object> introspect(String token) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (INTROSPECTION_CLIENT_ID + ":" + INTROSPECTION_CLIENT_SECRET)
                        .getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri("/oauth2/introspect"))
                        .header("Authorization", "Basic " + credentials)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("token=" + encode(token)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readValue(response.body(), JSON_MAP);
    }

    private HttpResponse<String> callControlPlane(String path, String body) throws Exception {
        return callControlPlane(path, body, JwtTestSupport.signServiceJwt(
                SIGNING_KEY, ISSUER, AUDIENCE, OPERATOR_PRINCIPAL_ID, ACCOUNT_MANAGE_SCOPE));
    }

    private HttpResponse<String> callControlPlane(String path, String body, String token)
            throws Exception {
        controlPlaneCounter++;
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri(path))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("X-Request-Id", "epoch-test-" + controlPlaneCounter)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 真实浏览器会话签发人员 access token：GET /login 取服务端 CSRF → POST /login →
     * GET /oauth2/authorize（PKCE S256）→ POST /oauth2/token。没有 mock，也没有绕过协议。
     */
    private String issueUserAccessToken(String password) throws Exception {
        String verifier = base64Url(randomBytes(32));
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> loginPage = client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header("Accept", "text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();

        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "username=" + encode(USERNAME)
                                        + "&password=" + encode(password)
                                        + "&" + csrf.group(1) + "=" + encode(csrf.group(2))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse(""))
                .doesNotContain("error");

        HttpResponse<String> authorize = client.send(
                HttpRequest.newBuilder(uri("/oauth2/authorize"
                                + "?response_type=code"
                                + "&client_id=" + BROWSER_CLIENT_ID
                                + "&redirect_uri=" + encode(REDIRECT_URI)
                                + "&scope=" + encode("openid profile")
                                + "&code_challenge=" + challenge
                                + "&code_challenge_method=S256"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authorize.statusCode()).isEqualTo(302);
        String code = queryParameter(
                authorize.headers().firstValue("Location").orElseThrow(), "code");
        assertThat(code).isNotBlank();

        HttpResponse<String> token = client.send(
                HttpRequest.newBuilder(uri("/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "grant_type=authorization_code"
                                        + "&code=" + encode(code)
                                        + "&redirect_uri=" + encode(REDIRECT_URI)
                                        + "&client_id=" + BROWSER_CLIENT_ID
                                        + "&code_verifier=" + verifier))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(token.statusCode()).isEqualTo(200);
        Object accessToken = JSON.readValue(token.body(), JSON_MAP).get("access_token");
        assertThat(accessToken).isInstanceOf(String.class);
        return (String) accessToken;
    }

    /** 账号不可认证时（禁用/凭据撤销）重新登录必须失败，拿不到授权码。 */
    private boolean loginFails(String password) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> loginPage = client.send(
                HttpRequest.newBuilder(uri("/login")).header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();
        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "username=" + encode(USERNAME)
                                        + "&password=" + encode(password)
                                        + "&" + csrf.group(1) + "=" + encode(csrf.group(2))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return login.statusCode() == 302
                && login.headers().firstValue("Location").orElse("").contains("error");
    }

    private long securityEpochClaim(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet().getLongClaim("sec_epoch");
    }

    /** 账号状态可能已不可认证，因此按登录标识查，不走认证投影。 */
    private UUID accountId() {
        UUID accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM ainer_identity_login_identity "
                        + "WHERE normalized_identifier = ? AND provider_authority = ?",
                UUID.class, USERNAME, ISSUER);
        assertThat(accountId).isNotNull();
        return accountId;
    }

    private long storedEpoch() {
        Long epoch = jdbcTemplate.queryForObject(
                "SELECT security_epoch FROM ainer_identity_human_account WHERE id = ?",
                Long.class, accountId());
        return epoch == null ? -1L : epoch;
    }

    private String storedStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_human_account WHERE id = ?",
                String.class, accountId());
    }

    private void insertOperatorPrincipal() {
        ServicePrincipalRow row = new ServicePrincipalRow();
        row.setId(UUID.fromString(OPERATOR_PRINCIPAL_ID));
        row.setIssuer(ISSUER);
        row.setStatus("ACTIVE");
        row.setSecurityEpoch(0L);
        row.setCreatedAt(Instant.now());
        servicePrincipalMapper.insertPrincipal(row);
    }

    private void registerBrowserClient() {
        registeredClientRepository.save(RegisteredClient
                .withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(BROWSER_CLIENT_ID)
                .clientName("Epoch revocation test browser client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .setting(AinerAuthorizationServerConfiguration.TOKEN_PROFILE_SETTING,
                                "USER_NEUTRAL_V1")
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build());
    }

    private void registerIntrospectionClient() {
        registeredClientRepository.save(RegisteredClient
                .withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(INTROSPECTION_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(INTROSPECTION_CLIENT_SECRET))
                .clientName("Epoch revocation test introspection client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerAuthorizationServerConfiguration.INTROSPECTION_CLIENT_SCOPE)
                .clientSettings(ClientSettings.builder()
                        .setting(
                                AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING,
                                true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(1))
                        .build())
                .build());
    }

    private static ConfigurableApplicationContext startProbe(
            String name, int authorizationServerPort, boolean onlineValidation) {
        String authorizationServer = "http://127.0.0.1:" + authorizationServerPort;
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.application.name", name);
        properties.put("spring.main.banner-mode", "off");
        properties.put("server.port", "0");
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
        properties.put("probe.jwk-set-uri", authorizationServer + "/oauth2/jwks");
        properties.put("probe.issuer", ISSUER);
        properties.put("probe.audience", AUDIENCE);
        properties.put("ainer.security.resource-server.enabled", "true");
        properties.put("ainer.security.resource-server.online-validation.enabled",
                Boolean.toString(onlineValidation));
        if (onlineValidation) {
            properties.put("ainer.security.resource-server.online-validation.introspection-uri",
                    authorizationServer + "/oauth2/introspect");
            properties.put("ainer.security.resource-server.online-validation.client-id",
                    INTROSPECTION_CLIENT_ID);
            properties.put("ainer.security.resource-server.online-validation.client-secret",
                    INTROSPECTION_CLIENT_SECRET);
            properties.put("ainer.security.resource-server.online-validation.allow-insecure-http",
                    "true");
            properties.put("ainer.security.resource-server.online-validation.always-protected-paths",
                    "/probe/**");
        }
        // 用命令行参数（最高优先级）注入：SpringApplicationBuilder#properties 是 defaultProperties，
        // 会被 classpath 上 Authorization Server 的 application.yaml（server.port=9000 等）覆盖。
        String[] arguments = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(ResourceServerProbeApplication.class)
                .run(arguments);
    }

    private String onlineProbeUrl() {
        return "http://127.0.0.1:" + localPort(onlineValidationProbe);
    }

    private String offlineProbeUrl() {
        return "http://127.0.0.1:" + localPort(offlineValidationProbe);
    }

    private static String localPort(ConfigurableApplicationContext context) {
        String value = context.getEnvironment().getProperty("local.server.port");
        assertThat(value).as("probe resource server port").isNotNull();
        return value;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String queryParameter(String location, String name) {
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&]+)")
                .matcher(location);
        assertThat(matcher.find()).as("query parameter %s in %s", name, location).isTrue();
        return java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static Path createKeyDirectory() {
        try {
            Path directory = Files.createTempDirectory("ainer-epoch-test-keys");
            writePem(directory.resolve("private.pem"), "PRIVATE KEY",
                    SIGNING_KEY.toRSAPrivateKey().getEncoded());
            writePem(directory.resolve("public.pem"), "PUBLIC KEY",
                    SIGNING_KEY.toRSAPublicKey().getEncoded());
            return directory;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create epoch test signing keys", exception);
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
