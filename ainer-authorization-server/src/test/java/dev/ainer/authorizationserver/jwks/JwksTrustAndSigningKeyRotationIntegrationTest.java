package dev.ainer.authorizationserver.jwks;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.authorizationserver.AinerAuthorizationServerApplication;
import dev.ainer.jwksprobe.JwksTrustResourceServerProbeApplication;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.security.principal.IdentityAuthorityRef;
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
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWKS 信任锚与签名密钥轮换的真实端到端证明。
 *
 * <p>被证明的是整条链上此前**没有任何自动化证据**的一跳：资源服务器从授权服务器的
 * {@code /oauth2/jwks} 端点、经真实 HTTP 取回公钥并完成验签。这里的资源服务器发行物
 * （{@link JwksTrustResourceServerProbeApplication}）不声明任何 {@code JwtDecoder}，解码器由
 * Spring Boot 按 {@code jwk-set-uri} + {@code issuer-uri} + {@code audiences} 构造，安全链来自框架
 * 自动装配——即 ainer-server 的生产装配路径，没有 {@code @Primary JwtDecoder} 之类的测试替身。
 *
 * <p>轮换时间线用三个真实的授权服务器上下文表达（同一 PostgreSQL、不同密钥目录），每一步都是可
 * 独立断言的状态，而不是「模拟」：
 *
 * <ol>
 *   <li>{@code before}：目录里只有旧 key，激活 key = 旧 key → 由它签发的 Token 记为在途 Token；</li>
 *   <li>{@code window}：目录里同时发布旧 key 与新 key，激活 key = 新 key（被测应用本身）→
 *       新 Token 用新 key 签、旧 Token 仍验签通过；</li>
 *   <li>{@code after}：旧 key 从目录移除 → JWKS 不再包含旧 kid，旧 Token 立刻被拒（未知 kid 失败
 *       关闭），新 Token 照常。</li>
 * </ol>
 *
 * <p>刻意不声明 {@code disabledWithoutDocker}：这是安全承诺的证据，跳过等于掩盖。
 */
@Testcontainers
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.security.authorization-server.audience=ainer-api",
                "ainer.security.authorization-server.signing-key-ring.active-key-id="
                        + JwksTrustAndSigningKeyRotationIntegrationTest.NEW_KEY_ID
        })
class JwksTrustAndSigningKeyRotationIntegrationTest {

    static final String ISSUER = "https://auth.ainer.test";
    static final String AUDIENCE = "ainer-api";
    /** 旧 key：轮换前签发在途 Token，轮换窗口内仍必须验签通过。 */
    static final String OLD_KEY_ID = "auth-2026h1";
    /** 新 key：轮换窗口起成为激活 key，此后所有新 Token 由它签发。 */
    static final String NEW_KEY_ID = "auth-2026h2";

    private static final String USERNAME = "jwks-rotation-user";
    private static final String PASSWORD = "jwks-rotation-password-2026";
    private static final String BROWSER_CLIENT_ID = "jwks-rotation-browser";
    private static final String REDIRECT_URI = "http://127.0.0.1:4712/callback";
    private static final String NEVER_PUBLISHED_KEY_ID = "auth-never-published";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"(_csrf)\"[^>]*value=\"([^\"]+)\"[^>]*>");

    private static final RSAKey OLD_KEY = generateRsaKey(OLD_KEY_ID);
    private static final RSAKey NEW_KEY = generateRsaKey(NEW_KEY_ID);
    private static final Path KEYS_ROOT = createKeysRoot();
    private static final Path BEFORE_ROTATION_KEYS = KEYS_ROOT.resolve("before-rotation");
    private static final Path ROTATION_WINDOW_KEYS = KEYS_ROOT.resolve("rotation-window");
    private static final Path AFTER_REMOVAL_KEYS = KEYS_ROOT.resolve("after-removal");

    static {
        writeKeyPair(BEFORE_ROTATION_KEYS, OLD_KEY_ID, OLD_KEY);
        // 过渡期：旧 key 仍发布（私钥也留在目录里，回滚用），新 key 成为激活 key。
        // 装载器只把激活 key 的私钥放进签名环，因此旧私钥在场也签不出新 Token。
        writeKeyPair(ROTATION_WINDOW_KEYS, OLD_KEY_ID, OLD_KEY);
        writeKeyPair(ROTATION_WINDOW_KEYS, NEW_KEY_ID, NEW_KEY);
        writeKeyPair(AFTER_REMOVAL_KEYS, NEW_KEY_ID, NEW_KEY);
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_jwks_rotation_test")
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
        registry.add("ainer.security.authorization-server.signing-key-ring.directory",
                () -> ROTATION_WINDOW_KEYS.toString());
    }

    /** 被测 Authorization Server 实例的端口（轮换过渡期：发布旧+新，激活新）。 */
    @LocalServerPort
    int port;

    @Autowired
    IdentityFoundationService foundationService;
    @Autowired
    RegisteredClientRepository registeredClientRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private static ConfigurableApplicationContext beforeRotationServer;
    private static ConfigurableApplicationContext afterRemovalServer;
    private static ConfigurableApplicationContext windowProbe;
    private static ConfigurableApplicationContext removalProbe;
    private static int beforeRotationPort;
    private static int afterRemovalPort;

    /**
     * 三个额外上下文只在第一次测试时启动：两个真实授权服务器实例（轮换前 / 移除后）与两个资源
     * 服务器探针（分别指向过渡期与移除后的授权服务器）。
     */
    private void startOnce() {
        if (windowProbe == null) {
            beforeRotationServer = startAuthorizationServer(
                    "jwks-before-rotation", BEFORE_ROTATION_KEYS, OLD_KEY_ID);
            beforeRotationPort = localPort(beforeRotationServer);
            afterRemovalServer = startAuthorizationServer(
                    "jwks-after-removal", AFTER_REMOVAL_KEYS, NEW_KEY_ID);
            afterRemovalPort = localPort(afterRemovalServer);
            windowProbe = startResourceServerProbe("jwks-window-probe", port);
            removalProbe = startResourceServerProbe("jwks-removal-probe", afterRemovalPort);
        }
    }

    @AfterAll
    static void stopExtraContexts() {
        for (ConfigurableApplicationContext context : new ConfigurableApplicationContext[] {
                windowProbe, removalProbe, beforeRotationServer, afterRemovalServer }) {
            if (context != null) {
                context.close();
            }
        }
    }

    @BeforeEach
    void prepareFixtures() {
        startOnce();
        jdbcTemplate.update("DELETE FROM ainer_identity_credential");
        jdbcTemplate.update("DELETE FROM ainer_identity_login_identity");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_account");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");

        foundationService.registerHumanAccountWithPassword(
                new IdentityAuthorityRef(ISSUER), LoginIdentityType.USERNAME, ISSUER, USERNAME, PASSWORD);
        registerBrowserClient();
    }

    @Test
    @DisplayName("资源服务器经真实 HTTP 从 JWKS 端点取公钥并验签成功（生产解码器，无测试替身）")
    void resourceServerVerifiesTokenFetchedOverRealJwksEndpoint() throws Exception {
        String token = issueAccessToken(port, PASSWORD);

        // 断言前置事实：Token 由激活 key 签发，且该 key 确实在真实 HTTP 的 JWKS 文档里
        assertThat(headerKeyId(token)).isEqualTo(NEW_KEY_ID);
        assertThat(publishedKeyIds(port)).containsExactlyInAnyOrder(OLD_KEY_ID, NEW_KEY_ID);
        assertThat(probeStatus(windowProbe, token)).isEqualTo(200);
    }

    @Test
    @DisplayName("JWKS 只发布公钥材料：私钥参数（d/p/q/dp/dq/qi）绝不出现在响应里")
    void jwksNeverExposesPrivateKeyMaterial() throws Exception {
        Map<String, Map<String, Object>> jwks = jwks(port);

        assertThat(jwks.keySet()).containsExactlyInAnyOrder(OLD_KEY_ID, NEW_KEY_ID);
        jwks.values().forEach(key -> assertThat(key)
                .containsEntry("kty", "RSA")
                .containsKeys("n", "e")
                .doesNotContainKeys("d", "p", "q", "dp", "dq", "qi"));
        // 发布的模数与本地私钥派生出的公钥一致：发布的是真实签名 key，不是占位符
        assertThat(jwks.get(NEW_KEY_ID).get("n")).isEqualTo(base64Url(newModulus()));
    }

    @Test
    @DisplayName("轮换过渡期：新 key 签发的 Token 验签成功，旧 key 签发的在途 Token 仍然验签成功")
    void previousKeyTokenStillVerifiesDuringRotationWindow() throws Exception {
        // 旧 Token 由真实授权服务器（轮换前实例，激活 key = 旧 key）走完整 PKCE 流程签发
        String legacyToken = issueAccessToken(beforeRotationPort, PASSWORD);
        String currentToken = issueAccessToken(port, PASSWORD);

        assertThat(headerKeyId(legacyToken)).isEqualTo(OLD_KEY_ID);
        assertThat(headerKeyId(currentToken)).isEqualTo(NEW_KEY_ID);
        assertThat(probeStatus(windowProbe, legacyToken)).isEqualTo(200);
        assertThat(probeStatus(windowProbe, currentToken)).isEqualTo(200);
    }

    @Test
    @DisplayName("移除旧 key 后：旧 Token 被拒（401），新 Token 仍然成功")
    void removedKeyIsImmediatelyRejected() throws Exception {
        String legacyToken = issueAccessToken(beforeRotationPort, PASSWORD);
        String currentToken = issueAccessToken(port, PASSWORD);

        // 对照：同一个旧 Token 在过渡期是可用的（其验签成功不依赖任何宽限期）
        assertThat(probeStatus(windowProbe, legacyToken)).isEqualTo(200);

        // 移除后：JWKS 不再发布旧 kid，资源服务器（新起的上下文，无旧 JWKS 缓存）失败关闭
        assertThat(publishedKeyIds(afterRemovalPort)).containsExactly(NEW_KEY_ID);
        assertThat(probeStatus(removalProbe, legacyToken)).isEqualTo(401);
        assertThat(probeStatus(removalProbe, currentToken)).isEqualTo(200);
    }

    @Test
    @DisplayName("篡改签名的 Token 被拒（401）：验签不是装饰")
    void tamperedTokenIsRejected() throws Exception {
        String token = issueAccessToken(port, PASSWORD);

        assertThat(probeStatus(windowProbe, tamperSignature(token))).isEqualTo(401);
        assertThat(probeStatus(windowProbe, token)).isEqualTo(200);
    }

    @Test
    @DisplayName("未发布的 kid 失败关闭：不降级成「认签名不认 key」")
    void unpublishedKidFailsClosed() throws Exception {
        RSAKey rogue = generateRsaKey(NEVER_PUBLISHED_KEY_ID);
        String rogueToken = signToken(rogue, NEVER_PUBLISHED_KEY_ID, ISSUER, AUDIENCE);

        assertThat(headerKeyId(rogueToken)).isEqualTo(NEVER_PUBLISHED_KEY_ID);
        assertThat(publishedKeyIds(port)).doesNotContain(NEVER_PUBLISHED_KEY_ID);
        assertThat(probeStatus(windowProbe, rogueToken)).isEqualTo(401);
    }

    @Test
    @DisplayName("走 jwk-set-uri 时 issuer 与 audience 仍然被校验，没有退化成「只验签名」")
    void issuerAndAudienceAreStillValidatedWhenJwkSetUriIsUsed() throws Exception {
        // 签名 key 合法（就是新 key），只有 iss / aud 不合法
        String forgedIssuer = signToken(NEW_KEY, NEW_KEY_ID, "https://evil.example", AUDIENCE);
        String forgedAudience = signToken(NEW_KEY, NEW_KEY_ID, ISSUER, "other-api");

        assertThat(probeStatus(windowProbe, forgedIssuer)).isEqualTo(401);
        assertThat(probeStatus(windowProbe, forgedAudience)).isEqualTo(401);
    }

    @Test
    @DisplayName("轮换窗口通过发布集而不是时间窗口表达：切换激活 key 不影响在途 Token 的验签")
    void activationSwitchKeepsPublishedKeySetStable() throws Exception {
        // 过渡期 JWKS = 旧 + 新；激活 key 只决定「谁来签」，不改变「谁能验」
        Map<String, Map<String, Object>> windowJwks = jwks(port);
        assertThat(windowJwks.keySet()).containsExactlyInAnyOrder(OLD_KEY_ID, NEW_KEY_ID);
        assertThat(headerKeyId(issueAccessToken(port, PASSWORD))).isEqualTo(NEW_KEY_ID);
        // 旧 key 私钥仍在过渡期目录中，但它已不能签发：授权服务器只把激活 key 的私钥放进签名环
        assertThat(probeStatus(windowProbe, signToken(OLD_KEY, OLD_KEY_ID, ISSUER, AUDIENCE))).isEqualTo(200);
    }

    /** 真实浏览器会话签发 access token：GET /login 取服务端 CSRF → POST /login → PKCE 授权码 → Token。 */
    private String issueAccessToken(int authorizationServerPort, String password) throws Exception {
        String verifier = base64Url(randomBytes(32));
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> loginPage = client.send(
                HttpRequest.newBuilder(uri(authorizationServerPort, "/login"))
                        .header("Accept", "text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();

        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(uri(authorizationServerPort, "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "username=" + encode(USERNAME)
                                        + "&password=" + encode(password)
                                        + "&" + csrf.group(1) + "=" + encode(csrf.group(2))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);

        HttpResponse<String> authorize = client.send(
                HttpRequest.newBuilder(uri(authorizationServerPort, "/oauth2/authorize"
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

        HttpResponse<String> token = client.send(
                HttpRequest.newBuilder(uri(authorizationServerPort, "/oauth2/token"))
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

    private int probeStatus(ConfigurableApplicationContext probe, String token) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(probeUrl(probe) + "/probe/secure"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    /** 直接读取授权服务器的 JWKS 文档（真实 HTTP，无鉴权，与资源服务器看到的完全一致）。 */
    private Map<String, Map<String, Object>> jwks(int authorizationServerPort) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri(authorizationServerPort, "/oauth2/jwks")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> document = JSON.readValue(response.body(), JSON_MAP);
        Map<String, Map<String, Object>> keys = new LinkedHashMap<>();
        for (Object key : (List<?>) document.get("keys")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> jwk = (Map<String, Object>) key;
            keys.put((String) jwk.get("kid"), jwk);
        }
        return keys;
    }

    private List<String> publishedKeyIds(int authorizationServerPort) throws Exception {
        return List.copyOf(jwks(authorizationServerPort).keySet());
    }

    private static String headerKeyId(String token) throws Exception {
        return SignedJWT.parse(token).getHeader().getKeyID();
    }

    /** 篡改签名段但保留头部与载荷：任何不真实验签的实现都会放它过去。 */
    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[signature.length - 1] ^= 0x01;
        return parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    /** 用指定 key 签一个真实 RSA Token（用于构造「未发布 kid / 伪造 iss / 伪造 aud」等负例）。 */
    private static String signToken(RSAKey key, String keyId, String issuer, String audience) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .subject("019c7100-0000-7000-8000-0000000000bb")
                        .issueTime(new java.util.Date())
                        .expirationTime(new java.util.Date(System.currentTimeMillis() + 300_000))
                        .build());
        jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
        return jwt.serialize();
    }

    private void registerBrowserClient() {
        registeredClientRepository.save(RegisteredClient
                .withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(BROWSER_CLIENT_ID)
                .clientName("JWKS rotation test browser client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .setting(dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration
                                .TOKEN_PROFILE_SETTING, "USER_NEUTRAL_V1")
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build());
    }

    private static ConfigurableApplicationContext startAuthorizationServer(
            String name, Path keyDirectory, String activeKeyId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.application.name", name);
        properties.put("spring.main.banner-mode", "off");
        properties.put("server.port", "0");
        properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("ainer.security.authorization-server.issuer", ISSUER);
        properties.put("ainer.security.authorization-server.audience", AUDIENCE);
        properties.put("ainer.security.authorization-server.signing-key-ring.directory", keyDirectory.toString());
        properties.put("ainer.security.authorization-server.signing-key-ring.active-key-id", activeKeyId);
        return new SpringApplicationBuilder(AinerAuthorizationServerApplication.class)
                .run(toArguments(properties));
    }

    /**
     * 资源服务器探针：只声明「验签公钥从哪来」和别人是谁，解码器与安全链全部交给生产装配。
     *
     * <p>{@code jwk-set-uri} 与 {@code issuer-uri} 同时存在时，Spring Boot 用前者取 JWKS、用后者
     * 校验 {@code iss}（{@code JwkSetUriCondition} 胜过 {@code IssuerUriCondition}），因此下面
     * 「伪 iss 被拒」的断言证明的是真实生产语义。
     */
    private static ConfigurableApplicationContext startResourceServerProbe(String name, int authorizationServerPort) {
        String authorizationServer = "http://127.0.0.1:" + authorizationServerPort;
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.application.name", name);
        properties.put("spring.main.banner-mode", "off");
        properties.put("server.port", "0");
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
        properties.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                authorizationServer + "/oauth2/jwks");
        properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", ISSUER);
        properties.put("spring.security.oauth2.resourceserver.jwt.audiences", AUDIENCE);
        properties.put("ainer.security.resource-server.enabled", "true");
        properties.put("ainer.security.resource-server.allow-insecure-jwk-set-http", "true");
        return new SpringApplicationBuilder(JwksTrustResourceServerProbeApplication.class)
                .run(toArguments(properties));
    }

    /**
     * 用命令行参数（最高优先级）注入：{@code SpringApplicationBuilder#properties} 是 defaultProperties，
     * 会被 classpath 上 Authorization Server 的 application.yaml（server.port=9000 等）覆盖。
     */
    private static String[] toArguments(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static String probeUrl(ConfigurableApplicationContext probe) {
        return "http://127.0.0.1:" + localPort(probe);
    }

    private static int localPort(ConfigurableApplicationContext context) {
        String value = context.getEnvironment().getProperty("local.server.port");
        assertThat(value).as("context local.server.port").isNotNull();
        return Integer.parseInt(value);
    }

    private static URI uri(int authorizationServerPort, String path) {
        return URI.create("http://127.0.0.1:" + authorizationServerPort + path);
    }

    private static String queryParameter(String location, String name) {
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&]+)").matcher(location);
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

    private static String base64Url(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return base64Url(bytes);
    }

    private static java.math.BigInteger newModulus() throws Exception {
        return ((RSAPublicKey) publicKey(AFTER_REMOVAL_KEYS, NEW_KEY_ID)).getModulus();
    }

    private static RSAKey generateRsaKey(String keyId) {
        try {
            return new RSAKeyGenerator(3072).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate test RSA key " + keyId, exception);
        }
    }

    private static Path createKeysRoot() {
        try {
            return Files.createTempDirectory("ainer-jwks-rotation-keys");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create JWKS rotation key root", exception);
        }
    }

    private static void writeKeyPair(Path directory, String keyId, RSAKey key) {
        try {
            Files.createDirectories(directory);
            writePem(directory.resolve(keyId + ".private.pem"), "PRIVATE KEY",
                    key.toRSAPrivateKey().getEncoded());
            writePem(directory.resolve(keyId + ".public.pem"), "PUBLIC KEY",
                    key.toRSAPublicKey().getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write test signing keys", exception);
        }
    }

    /** 从 PEM 读回公钥：断言 JWKS 发布的就是这把 key 的真实模数。 */
    private static RSAPublicKey publicKey(Path directory, String keyId) throws Exception {
        String pem = Files.readString(directory.resolve(keyId + ".public.pem"), StandardCharsets.US_ASCII);
        byte[] der = Base64.getDecoder().decode(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        Files.writeString(path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }
}
