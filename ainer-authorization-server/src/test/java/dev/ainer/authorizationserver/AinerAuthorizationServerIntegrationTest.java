package dev.ainer.authorizationserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityAccessLifecycleService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.foundation.ServicePrincipal;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.TokenProfile;
import dev.ainer.web.request.RequestIds;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.identity.directory-api.enabled=true",
                "ainer.security.authorization-server.client-control.enabled=true",
                "ainer.security.authorization-server.client-control.operator-client-ids="
                        + "ainer-client-operator-test",
                "ainer.security.authorization-server.client-control.allowed-scopes="
                        + "ai.invoke,identity.directory.read",
                "ainer.security.authorization-server.browser-client-control.enabled=true",
                "ainer.security.authorization-server.browser-client-control.operator-client-ids="
                        + "ainer-browser-operator-test",
                "ainer.security.authorization-server.browser-client-control.allowed-scopes="
                        + "openid,profile,tenant.members.read",
                "ainer.identity.platform-control.enabled=true",
                "ainer.identity.platform-control.operator-client-ids="
                        + "ainer-platform-identity-operator-test,"
                        + "ainer-platform-identity-limited-test",
                "ainer.identity.platform-control.request-ttl=7d",
                "ainer.identity.platform-control.activation-ttl=24h",
                "ainer.identity.platform-control.activation-max-attempts=5",
                "ainer.identity.platform-control.notification-protection-active-key-version=test-v1",
                "ainer.identity.platform-control.notification-protection-keys="
                        + "test-v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "ainer.identity.provisioning-notification-receipts.enabled=true",
                "ainer.identity.provisioning-notification-receipts.gateway-client-ids="
                        + "ainer-notification-gateway-test",
                "spring.main.banner-mode=off"
        })
@Import(AinerAuthorizationServerIntegrationTest.TestKeyConfiguration.class)
class AinerAuthorizationServerIntegrationTest {

    private static final String CLIENT_ID = "ainer-machine-test";
    private static final String CLIENT_SECRET = "machine-secret-2026";
    private static final String TENANT_ID = "tenant:machine-test";
    private static final String CLIENT_OPERATOR_ID = "ainer-client-operator-test";
    private static final String BROWSER_OPERATOR_ID = "ainer-browser-operator-test";
    private static final String PLATFORM_IDENTITY_OPERATOR_ID =
            "ainer-platform-identity-operator-test";
    private static final String PLATFORM_IDENTITY_LIMITED_ID =
            "ainer-platform-identity-limited-test";
    private static final String NOTIFICATION_GATEWAY_ID =
            "ainer-notification-gateway-test";
    private static final String NOTIFICATION_RECEIPT_SCOPE =
            "identity.provisioning-notifications.receipts.write";
    private static final String PLATFORM_IDENTITY_SCOPES =
            "platform.tenants.read platform.tenants.write "
                    + "platform.users.read platform.users.write";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_authorization_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private IdentityApplicationService identityService;

    @Autowired
    private ServicePrincipalFoundationService servicePrincipalFoundationService;

    @Autowired
    private IdentityAccessLifecycleService identityAccessLifecycleService;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_identity_platform_operation_audit");
        jdbcTemplate.update(
                "DELETE FROM ainer_identity_notification_delivery_receipt");
        jdbcTemplate.update("DELETE FROM ainer_identity_notification_outbox");
        jdbcTemplate.update("DELETE FROM ainer_identity_activation_grant");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant_provisioning_request");
        jdbcTemplate.update("DELETE FROM ainer_identity_ownership_recovery");
        jdbcTemplate.update("DELETE FROM ainer_identity_ownership_transfer");
        jdbcTemplate.update("DELETE FROM ainer_identity_member_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event_replay_request");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_passkey_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_passkey_enrollment_grant");
        jdbcTemplate.update("DELETE FROM ainer_passkey_recovery_lockout");
        jdbcTemplate.update("DELETE FROM ainer_passkey_recovery_request");
        jdbcTemplate.update("DELETE FROM ainer_passkey_recovery_code");
        jdbcTemplate.update("DELETE FROM ainer_passkey_credential_audit");
        jdbcTemplate.update("DELETE FROM ainer_passkey_credential");
        jdbcTemplate.update("DELETE FROM user_credentials");
        jdbcTemplate.update("DELETE FROM user_entities");
        jdbcTemplate.update("DELETE FROM ainer_oauth_browser_client_audit");
        jdbcTemplate.update("DELETE FROM ainer_oauth_browser_client");
        jdbcTemplate.update("DELETE FROM ainer_oauth_service_client_audit");
        jdbcTemplate.update("DELETE FROM ainer_oauth_service_client");
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");
        registeredClientRepository.save(machineClient());
        registeredClientRepository.save(machineClient(
                CLIENT_OPERATOR_ID, null, "oauth.clients.manage"));
        registeredClientRepository.save(machineClient(
                BROWSER_OPERATOR_ID, null, "oauth.browser-clients.manage"));
        registeredClientRepository.save(machineClient(
                PLATFORM_IDENTITY_OPERATOR_ID,
                null,
                "platform.tenants.read",
                "platform.tenants.write",
                "platform.users.read",
                "platform.users.write"));
        registeredClientRepository.save(machineClient(
                PLATFORM_IDENTITY_LIMITED_ID,
                null,
                "platform.tenants.write"));
        registeredClientRepository.save(machineClient(
                NOTIFICATION_GATEWAY_ID,
                null,
                NOTIFICATION_RECEIPT_SCOPE));
    }

    @Test
    void migratesIdentityAndOfficialJdbcProtocolStores() {
        assertThat(flyway.info().applied()).hasSize(24);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('oauth2_registered_client','oauth2_authorization',"
                        + "'oauth2_authorization_consent','ainer_oauth_service_client',"
                        + "'ainer_oauth_service_client_audit','user_entities','user_credentials',"
                        + "'ainer_passkey_credential','ainer_passkey_credential_audit',"
                        + "'ainer_passkey_recovery_code','ainer_passkey_recovery_lockout',"
                        + "'ainer_passkey_recovery_request','ainer_passkey_security_operation_audit',"
                        + "'ainer_passkey_enrollment_grant','ainer_identity_member_audit',"
                        + "'ainer_identity_tenant_provisioning_request',"
                        + "'ainer_identity_platform_operation_audit',"
                        + "'ainer_identity_activation_grant',"
                        + "'ainer_identity_notification_outbox',"
                        + "'ainer_identity_notification_delivery_receipt',"
                        + "'ainer_identity_human_account',"
                        + "'ainer_identity_login_identity',"
                        + "'ainer_identity_service_principal',"
                        + "'ainer_identity_oauth_client_binding',"
                        + "'ainer_identity_credential',"
                        + "'ainer_identity_human_profile')",
                Integer.class)).isEqualTo(26);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void platformIdentityProvisioningIsTenantlessScopedIdempotentAndAudited() throws Exception {
        String operatorToken =
                accessToken(PLATFORM_IDENTITY_OPERATOR_ID, PLATFORM_IDENTITY_SCOPES);
        Map<String, String> body = Map.of(
                "tenantCode", "HTTP-NEXT",
                "tenantName", "HTTP Next",
                "ownerUsername", "OWNER@HTTP-NEXT.DEV",
                "ownerDisplayName", "HTTP Owner",
                "deliveryChannel", "EMAIL",
                "deliveryAddress", "owner@http-next.dev",
                "changeReference", "ORDER-HTTP-001");

        HttpResponse<String> created = platformProvisioningPost(
                operatorToken, "idem-http-next", body);
        HttpResponse<String> replayed = platformProvisioningPost(
                operatorToken, "idem-http-next", body);

        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(created.headers().firstValue("cache-control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        JsonNode createdData = objectMapper.readTree(created.body()).path("data");
        JsonNode replayedData = objectMapper.readTree(replayed.body()).path("data");
        String provisioningRequestId = createdData.path("id").stringValue();
        assertThat(createdData.path("created").booleanValue()).isTrue();
        assertThat(replayedData.path("created").booleanValue()).isFalse();
        assertThat(replayedData.path("id").stringValue()).isEqualTo(provisioningRequestId);
        assertThat(createdData.path("tenantCode").stringValue()).isEqualTo("http-next");
        assertThat(createdData.path("ownerUsername").stringValue())
                .isEqualTo("owner@http-next.dev");
        assertThat(createdData.path("status").stringValue()).isEqualTo("REQUESTED");
        assertThat(created.body())
                .doesNotContain("requestFingerprint")
                .doesNotContain("idempotencyKey")
                .doesNotContain("activationToken")
                .doesNotContain("secret");

        HttpResponse<String> found = internalGet(
                "/internal/platform/identity/tenant-provisioning-requests/"
                        + provisioningRequestId,
                operatorToken);
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(found.body()).path("data").path("id").stringValue())
                .isEqualTo(provisioningRequestId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ?::uuid AND phase = 'REQUESTED'",
                Integer.class,
                provisioningRequestId)).isEqualTo(1);

        HttpResponse<String> changedIdempotency = platformProvisioningPost(
                operatorToken,
                "idem-http-next",
                Map.of(
                        "tenantCode", "HTTP-NEXT",
                        "tenantName", "Changed Name",
                        "ownerUsername", "OWNER@HTTP-NEXT.DEV",
                        "ownerDisplayName", "HTTP Owner",
                        "deliveryChannel", "EMAIL",
                        "deliveryAddress", "owner@http-next.dev",
                        "changeReference", "ORDER-HTTP-001"));
        assertThat(changedIdempotency.statusCode()).isEqualTo(409);
        assertThat(changedIdempotency.headers().firstValue("cache-control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(objectMapper.readTree(changedIdempotency.body()).path("code").stringValue())
                .isEqualTo("AINER.IDENTITY.TENANT_PROVISIONING_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void platformIdentityCancellationIsExplicitIdempotentAndDestroysPayload()
            throws Exception {
        String operatorToken =
                accessToken(PLATFORM_IDENTITY_OPERATOR_ID, PLATFORM_IDENTITY_SCOPES);
        HttpResponse<String> created = platformProvisioningPost(
                operatorToken,
                "idem-http-cancel",
                Map.of(
                        "tenantCode", "http-cancel",
                        "tenantName", "HTTP Cancel",
                        "ownerUsername", "owner@http-cancel.dev",
                        "ownerDisplayName", "HTTP Cancel Owner",
                        "deliveryChannel", "EMAIL",
                        "deliveryAddress", "owner@http-cancel.dev",
                        "changeReference", "ORDER-HTTP-CREATE"));
        String provisioningRequestId = objectMapper
                .readTree(created.body())
                .path("data")
                .path("id")
                .stringValue();
        String cancellationPath =
                "/internal/platform/identity/tenant-provisioning-requests/"
                        + provisioningRequestId
                        + "/cancellations";

        HttpResponse<String> cancelled = internalPost(
                cancellationPath,
                operatorToken,
                Map.of("changeReference", "ORDER-HTTP-CANCEL"));
        HttpResponse<String> replayed = internalPost(
                cancellationPath,
                operatorToken,
                Map.of("changeReference", "ORDER-HTTP-CANCEL-REPLAY"));

        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(cancelled.body())
                .path("data")
                .path("status")
                .stringValue()).isEqualTo("CANCELLED");
        assertThat(objectMapper.readTree(replayed.body())
                .path("data")
                .path("status")
                .stringValue()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_activation_grant "
                        + "WHERE provisioning_request_id = ?::uuid",
                String.class,
                provisioningRequestId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, payload_key_version, "
                        + "octet_length(protected_payload) AS payload_length, "
                        + "payload_destroyed_at IS NOT NULL AS payload_destroyed "
                        + "FROM ainer_identity_notification_outbox "
                        + "WHERE provisioning_request_id = ?::uuid",
                provisioningRequestId))
                .containsEntry("publication_status", "CANCELLED")
                .containsEntry("payload_key_version", "destroyed")
                .containsEntry("payload_length", 32)
                .containsEntry("payload_destroyed", true);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT actor_type, actor_id, change_reference "
                        + "FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ?::uuid AND phase = 'CANCELLED'",
                provisioningRequestId))
                .containsEntry("actor_type", "SERVICE")
                .containsEntry("actor_id", PLATFORM_IDENTITY_OPERATOR_ID)
                .containsEntry("change_reference", "ORDER-HTTP-CANCEL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ?::uuid AND phase = 'CANCELLED'",
                Integer.class,
                provisioningRequestId)).isEqualTo(1);
    }

    @Test
    void platformIdentityListsAreBoundedScopedAndExcludeCredentialData()
            throws Exception {
        identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "list-alpha",
                "List Alpha",
                "alpha-owner@example.com",
                "strong-password-2026",
                "Alpha Owner"));
        identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "list-beta",
                "List Beta",
                "beta-owner@example.com",
                "strong-password-2026",
                "Beta Owner"));
        String operatorToken =
                accessToken(PLATFORM_IDENTITY_OPERATOR_ID, PLATFORM_IDENTITY_SCOPES);

        HttpResponse<String> tenants = internalGet(
                "/internal/platform/identity/tenants?page=1&size=1",
                operatorToken);
        HttpResponse<String> users = internalGet(
                "/internal/platform/identity/users?page=1&size=20",
                operatorToken);

        assertThat(tenants.statusCode()).isEqualTo(200);
        JsonNode tenantPage = objectMapper.readTree(tenants.body()).path("data");
        assertThat(tenantPage.path("page").intValue()).isEqualTo(1);
        assertThat(tenantPage.path("size").intValue()).isEqualTo(1);
        assertThat(tenantPage.path("total").longValue()).isEqualTo(2);
        assertThat(tenantPage.path("items").size()).isEqualTo(1);
        assertThat(tenantPage.path("items").get(0).path("code").stringValue())
                .isEqualTo("list-alpha");

        assertThat(users.statusCode()).isEqualTo(200);
        JsonNode userPage = objectMapper.readTree(users.body()).path("data");
        assertThat(userPage.path("total").longValue()).isEqualTo(2);
        assertThat(userPage.path("items").size()).isEqualTo(2);
        assertThat(users.body())
                .doesNotContain("password")
                .doesNotContain("clientSecret")
                .doesNotContain("activationSecret")
                .doesNotContain("deliveryAddress");

        assertThat(internalGet(
                "/internal/platform/identity/users",
                accessToken(
                        PLATFORM_IDENTITY_LIMITED_ID,
                        "platform.tenants.write"))
                .statusCode()).isEqualTo(403);
        assertThat(internalGet(
                "/internal/platform/identity/tenants?size=101",
                operatorToken)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void notificationGatewayReceiptIsTenantlessScopedIdempotentAndSafe()
            throws Exception {
        String operatorToken =
                accessToken(PLATFORM_IDENTITY_OPERATOR_ID, PLATFORM_IDENTITY_SCOPES);
        HttpResponse<String> provisioning = platformProvisioningPost(
                operatorToken,
                "idem-http-receipt",
                Map.of(
                        "tenantCode", "http-receipt",
                        "tenantName", "HTTP Receipt",
                        "ownerUsername", "receipt-owner@example.com",
                        "ownerDisplayName", "Receipt Owner",
                        "deliveryChannel", "EMAIL",
                        "deliveryAddress", "receipt-owner@example.com",
                        "changeReference", "ORDER-HTTP-RECEIPT"));
        assertThat(provisioning.statusCode()).isEqualTo(200);
        String provisioningRequestId = objectMapper.readTree(provisioning.body())
                .path("data")
                .path("id")
                .stringValue();
        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM ainer_identity_notification_outbox "
                        + "WHERE provisioning_request_id = ?::uuid",
                UUID.class,
                provisioningRequestId);
        jdbcTemplate.update(
                "UPDATE ainer_identity_notification_outbox "
                        + "SET publication_status = 'PUBLISHED', "
                        + "published_at = CURRENT_TIMESTAMP, "
                        + "payload_key_version = 'destroyed', "
                        + "protected_payload = decode(repeat('00', 32), 'hex'), "
                        + "payload_destroyed_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                notificationId);

        Instant occurredAt = Instant.now().minusSeconds(1);
        Map<String, String> receipt = Map.of(
                "eventId", "gateway-http-event-1",
                "notificationId", notificationId.toString(),
                "status", "DELIVERED",
                "occurredAt", occurredAt.toString());
        String path =
                "/internal/identity/tenant-provisioning-notification-receipts";

        assertThat(internalPost(path, null, receipt).statusCode()).isEqualTo(401);
        assertThat(internalPost(
                path,
                actorToken(
                        NOTIFICATION_GATEWAY_ID,
                        null,
                        "SERVICE",
                        "platform.users.read"),
                receipt).statusCode()).isEqualTo(403);
        assertThat(internalPost(
                path,
                actorToken(
                        NOTIFICATION_GATEWAY_ID,
                        UUID.randomUUID().toString(),
                        "SERVICE",
                        NOTIFICATION_RECEIPT_SCOPE),
                receipt).statusCode()).isEqualTo(403);
        assertThat(internalPost(
                path,
                actorToken(
                        "unknown-notification-gateway",
                        null,
                        "SERVICE",
                        NOTIFICATION_RECEIPT_SCOPE),
                receipt).statusCode()).isEqualTo(403);

        String gatewayToken =
                accessToken(NOTIFICATION_GATEWAY_ID, NOTIFICATION_RECEIPT_SCOPE);
        HttpResponse<String> created =
                internalPost(path, gatewayToken, receipt);
        HttpResponse<String> replayed =
                internalPost(path, gatewayToken, receipt);

        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.headers().firstValue("cache-control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        JsonNode createdBody = objectMapper.readTree(created.body());
        assertThat(createdBody.path("data").path("created").booleanValue()).isTrue();
        assertThat(createdBody.path("data").path("status").stringValue())
                .isEqualTo("DELIVERED");
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(replayed.body())
                .path("data")
                .path("created")
                .booleanValue()).isFalse();
        assertThat(created.body().toLowerCase())
                .doesNotContain(
                        "receipt-owner@example.com",
                        "secret",
                        "payload",
                        "token");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) "
                        + "FROM ainer_identity_notification_delivery_receipt "
                        + "WHERE notification_id = ?",
                Integer.class,
                notificationId)).isEqualTo(1);
    }

    @Test
    void platformIdentityProvisioningRejectsAnonymousWrongActorTenantScopeAndOperator()
            throws Exception {
        Map<String, String> body = Map.of(
                "tenantCode", "secure-provisioning",
                "tenantName", "Secure Provisioning",
                "ownerUsername", "secure-owner@example.com",
                "ownerDisplayName", "Secure Owner",
                "deliveryChannel", "EMAIL",
                "deliveryAddress", "secure-owner@example.com",
                "changeReference", "ORDER-HTTP-SECURE");

        assertThat(platformProvisioningPost(null, "idem-anonymous", body).statusCode())
                .isEqualTo(401);
        assertThat(platformProvisioningPost(
                accessToken(PLATFORM_IDENTITY_OPERATOR_ID, PLATFORM_IDENTITY_SCOPES),
                null,
                body).statusCode()).isEqualTo(400);
        assertThat(platformProvisioningPost(
                accessToken(PLATFORM_IDENTITY_LIMITED_ID, "platform.tenants.write"),
                "idem-limited",
                body).statusCode()).isEqualTo(403);
        assertThat(platformProvisioningPost(
                actorToken(
                        PLATFORM_IDENTITY_OPERATOR_ID,
                        UUID.randomUUID().toString(),
                        "SERVICE",
                        PLATFORM_IDENTITY_SCOPES),
                "idem-tenant-bound",
                body).statusCode()).isEqualTo(403);
        assertThat(platformProvisioningPost(
                actorToken(
                        PLATFORM_IDENTITY_OPERATOR_ID,
                        UUID.randomUUID().toString(),
                        "USER",
                        PLATFORM_IDENTITY_SCOPES),
                "idem-user",
                body).statusCode()).isEqualTo(403);

        String unknownOperatorId = "unknown-platform-operator";
        registeredClientRepository.save(machineClient(
                unknownOperatorId,
                null,
                "platform.tenants.read",
                "platform.tenants.write",
                "platform.users.read",
                "platform.users.write"));
        assertThat(platformProvisioningPost(
                accessToken(unknownOperatorId, PLATFORM_IDENTITY_SCOPES),
                "idem-unknown",
                body).statusCode()).isEqualTo(403);
        assertThat(internalGet(
                "/internal/platform/identity/tenant-provisioning-requests/"
                        + UUID.randomUUID(),
                accessToken(PLATFORM_IDENTITY_LIMITED_ID, "platform.tenants.write"))
                .statusCode()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant_provisioning_request",
                Integer.class)).isZero();
    }

    @Test
    void managedServiceClientSupportsOneTimeSecretBlueGreenRotationAndRetirement() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String operatorToken = accessToken(CLIENT_OPERATOR_ID, "oauth.clients.manage");

        HttpResponse<String> created = internalPost(
                "/internal/oauth-service-clients",
                operatorToken,
                Map.of(
                        "clientId", "orders-agent-v1",
                        "clientName", "Orders Agent",
                        "tenantId", tenantId.toString(),
                        "scopes", List.of("ai.invoke"),
                        "changeReference", "CHG-2026-1001"));

        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode createdBody = objectMapper.readTree(created.body());
        String firstSecret = createdBody.path("data").path("clientSecret").stringValue();
        assertThat(firstSecret).hasSizeGreaterThanOrEqualTo(43);
        assertThat(createdBody.path("data").path("client").path("status").stringValue())
                .isEqualTo("ACTIVE");
        String firstHash = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM oauth2_registered_client WHERE client_id = ?",
                String.class,
                "orders-agent-v1");
        assertThat(firstHash).isNotEqualTo(firstSecret);
        assertThat(passwordEncoder.matches(firstSecret, firstHash)).isTrue();
        HttpResponse<String> firstTokenResponse =
                token("orders-agent-v1", firstSecret, "ai.invoke");
        assertThat(firstTokenResponse.statusCode()).isEqualTo(200);
        String firstAccessToken = objectMapper.readTree(firstTokenResponse.body())
                .path("access_token").stringValue();
        String introspectionClientId = "ainer-managed-introspection-test";
        registeredClientRepository.save(machineClient(
                introspectionClientId, null, true, "token.introspect"));
        assertThat(objectMapper.readTree(introspect(firstAccessToken, introspectionClientId).body())
                        .path("active").booleanValue())
                .isTrue();

        HttpResponse<String> rotated = internalPost(
                "/internal/oauth-service-clients/orders-agent-v1/rotations",
                operatorToken,
                Map.of(
                        "replacementClientId", "orders-agent-v2",
                        "replacementClientName", "Orders Agent v2",
                        "changeReference", "CHG-2026-1002"));

        assertThat(rotated.statusCode()).isEqualTo(200);
        JsonNode rotatedBody = objectMapper.readTree(rotated.body());
        String replacementSecret = rotatedBody.path("data").path("clientSecret").stringValue();
        assertThat(rotatedBody.path("data").path("client").path("replacesClientId").stringValue())
                .isEqualTo("orders-agent-v1");
        assertThat(token("orders-agent-v1", firstSecret, "ai.invoke").statusCode()).isEqualTo(200);
        assertThat(token("orders-agent-v2", replacementSecret, "ai.invoke").statusCode()).isEqualTo(200);

        HttpResponse<String> retired = internalPost(
                "/internal/oauth-service-clients/orders-agent-v1/retirement",
                operatorToken,
                Map.of("changeReference", "CHG-2026-1003"));

        assertThat(retired.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(retired.body())
                        .path("data").path("status").stringValue())
                .isEqualTo("RETIRED");
        assertThat(token("orders-agent-v1", firstSecret, "ai.invoke").statusCode()).isEqualTo(401);
        assertThat(token("orders-agent-v2", replacementSecret, "ai.invoke").statusCode()).isEqualTo(200);
        assertThat(registeredClientRepository.findByClientId("orders-agent-v1")).isNull();
        assertThat(objectMapper.readTree(introspect(firstAccessToken, introspectionClientId).body())
                        .path("active").booleanValue())
                .isFalse();

        HttpResponse<String> retiredView = internalGet(
                "/internal/oauth-service-clients/orders-agent-v1", operatorToken);
        assertThat(retiredView.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(retiredView.body())
                        .path("data").path("status").stringValue())
                .isEqualTo("RETIRED");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT operation FROM ainer_oauth_service_client_audit "
                                + "WHERE client_id IN (?, ?)",
                        String.class,
                        "orders-agent-v1",
                        "orders-agent-v2"))
                .containsExactlyInAnyOrder("CREATED", "ROTATED", "RETIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'ainer_oauth_service_client_audit' "
                        + "AND column_name LIKE '%secret%'",
                Integer.class)).isZero();
    }

    @Test
    void clientControlRejectsTenantBoundOperatorAndUnapprovedScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String tenantOperatorId = "tenant-client-operator-test";
        registeredClientRepository.save(machineClient(
                tenantOperatorId, tenantId.toString(), "oauth.clients.manage"));
        String tenantOperatorToken = accessToken(tenantOperatorId, "oauth.clients.manage");

        HttpResponse<String> forbidden = internalPost(
                "/internal/oauth-service-clients",
                tenantOperatorToken,
                Map.of(
                        "clientId", "forbidden-agent",
                        "clientName", "Forbidden Agent",
                        "tenantId", tenantId.toString(),
                        "scopes", List.of("ai.invoke"),
                        "changeReference", "CHG-2026-2001"));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        String operatorToken = accessToken(CLIENT_OPERATOR_ID, "oauth.clients.manage");
        HttpResponse<String> invalidScope = internalPost(
                "/internal/oauth-service-clients",
                operatorToken,
                Map.of(
                        "clientId", "privileged-agent",
                        "clientName", "Privileged Agent",
                        "tenantId", tenantId.toString(),
                        "scopes", List.of("token.introspect"),
                        "changeReference", "CHG-2026-2002"));
        assertThat(invalidScope.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(invalidScope.body()).path("code").stringValue())
                .isEqualTo("AINER.AUTHORIZATION.OAUTH_CLIENT_SCOPE_NOT_ALLOWED");
    }

    @Test
    void clientCredentialsIssuesTenantScopedJwtAndPersistsAuthorization() throws Exception {
        HttpResponse<String> response = token(CLIENT_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("token_type").stringValue()).isEqualToIgnoringCase("Bearer");
        assertThat(body.path("scope").stringValue()).contains("ai.invoke");

        Jwt jwt = jwtDecoder.decode(body.path("access_token").stringValue());
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://auth.ainer.test");
        assertThat(jwt.getAudience()).containsExactly("ainer-api");
        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getClaimAsString("actor_type")).isEqualTo("SERVICE");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(TENANT_ID);
        assertThat(jwt.getClaimAsStringList("scope")).contains("ai.invoke");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void serviceProfileClientCredentialsIssuesStablePrincipalJwt() throws Exception {
        String clientId = "ainer-service-v1-test";
        ServicePrincipal principal = servicePrincipalFoundationService.registerServicePrincipal(
                new IdentityAuthorityRef("https://auth.ainer.test"));
        servicePrincipalFoundationService.bindClient(principal.principalId(), clientId);
        registeredClientRepository.save(machineClient(
                clientId, null, TokenProfile.SERVICE_V1.claimValue(), false, "ai.invoke"));

        HttpResponse<String> response = token(clientId, CLIENT_SECRET, "ai.invoke");

        assertThat(response.statusCode()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(
                objectMapper.readTree(response.body()).path("access_token").stringValue());
        assertThat(jwt.getSubject()).isEqualTo(principal.principalId().toString());
        assertThat(jwt.getSubject()).isNotEqualTo(clientId);
        assertThat(jwt.getClaimAsString("token_profile")).isEqualTo(TokenProfile.SERVICE_V1.claimValue());
        assertThat(jwt.getClaimAsString("claim_contract_version"))
                .isEqualTo(TokenProfile.CURRENT_CONTRACT_VERSION);
        assertThat(jwt.getClaimAsString("actor_type")).isEqualTo("SERVICE");
        assertThat(((Number) jwt.getClaim("sec_epoch")).longValue()).isEqualTo(0L);
        assertThat(jwt.getClaims()).doesNotContainKey("tenant_id");
    }

    @Test
    void serviceProfileClientWithoutBindingFailsClosed() throws Exception {
        String clientId = "ainer-service-v1-unbound";
        registeredClientRepository.save(machineClient(
                clientId, null, TokenProfile.SERVICE_V1.claimValue(), false, "ai.invoke"));

        HttpResponse<String> response = token(clientId, CLIENT_SECRET, "ai.invoke");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("error").stringValue()).isEqualTo("access_denied");
        assertThat(body.path("error_description").stringValue())
                .isEqualTo("No ACTIVE ServicePrincipal bound to client " + clientId);
    }

    @Test
    void browserClientControlCreatesRotatesAndRetiresPublicPkceClients() throws Exception {
        String operatorToken = accessToken(BROWSER_OPERATOR_ID, "oauth.browser-clients.manage");
        String redirectUri = "https://app.ainer.test/auth/callback";
        String postLogoutUri = "https://app.ainer.test/auth/logged-out";

        HttpResponse<String> created = internalPost(
                "/internal/oauth-browser-clients", operatorToken, Map.of(
                        "clientId", "ainer-app-test",
                        "clientName", "Ainer App Test",
                        "redirectUri", redirectUri,
                        "postLogoutRedirectUri", postLogoutUri,
                        "scopes", java.util.List.of("openid", "profile", "tenant.members.read"),
                        "changeReference", "TICKET-001"));
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("\"clientId\":\"ainer-app-test\"").contains("\"status\":\"ACTIVE\"");

        HttpResponse<String> found = internalGet(
                "/internal/oauth-browser-clients/ainer-app-test", operatorToken);
        assertThat(found.statusCode()).isEqualTo(200);

        HttpResponse<String> listed = internalGet(
                "/internal/oauth-browser-clients?page=1&size=20", operatorToken);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("\"total\":1");

        String wrongScopeToken = accessToken(CLIENT_OPERATOR_ID, "oauth.clients.manage");
        HttpResponse<String> forbidden = internalPost(
                "/internal/oauth-browser-clients", wrongScopeToken, Map.of(
                        "clientId", "should-fail", "clientName", "Fail",
                        "redirectUri", redirectUri, "postLogoutRedirectUri", postLogoutUri,
                        "scopes", java.util.List.of("openid"), "changeReference", "x"));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> duplicate = internalPost(
                "/internal/oauth-browser-clients", operatorToken, Map.of(
                        "clientId", "ainer-app-test", "clientName", "Dup",
                        "redirectUri", redirectUri, "postLogoutRedirectUri", postLogoutUri,
                        "scopes", java.util.List.of("openid"), "changeReference", "dup"));
        assertThat(duplicate.statusCode()).isEqualTo(409);

        HttpResponse<String> rotated = internalPost(
                "/internal/oauth-browser-clients/ainer-app-test/rotations", operatorToken, Map.of(
                        "replacementClientId", "ainer-app-test-v2",
                        "replacementClientName", "Ainer App Test v2",
                        "redirectUri", redirectUri, "postLogoutRedirectUri", postLogoutUri,
                        "changeReference", "rotate"));
        assertThat(rotated.statusCode()).isEqualTo(200);
        assertThat(rotated.body()).contains("\"replacesClientId\":\"ainer-app-test\"");

        HttpResponse<String> retired = internalPost(
                "/internal/oauth-browser-clients/ainer-app-test/retirement", operatorToken, Map.of(
                        "changeReference", "retire"));
        assertThat(retired.statusCode()).isEqualTo(200);
        assertThat(retired.body()).contains("\"status\":\"RETIRED\"");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_oauth_browser_client_audit WHERE client_id = ?",
                Integer.class, "ainer-app-test")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_oauth_browser_client_audit WHERE client_id = ?",
                Integer.class, "ainer-app-test-v2")).isEqualTo(1);
    }

    @Test
    void invalidClientSecretCannotReceiveToken() throws Exception {
        HttpResponse<String> response = token("wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class))
                .isZero();
    }

    @Test
    void onlyDedicatedClientCanIntrospectAndRfc7009RevocationMakesTokenInactive() throws Exception {
        String introspectionClientId = "ainer-introspection-test";
        registeredClientRepository.save(machineClient(
                introspectionClientId, null, true, "token.introspect"));
        String accessToken = accessToken(CLIENT_ID, "ai.invoke");

        HttpResponse<String> ordinaryClient = introspect(accessToken, CLIENT_ID);
        HttpResponse<String> active = introspect(accessToken, introspectionClientId);
        HttpResponse<String> revoked = revoke(accessToken, CLIENT_ID);
        HttpResponse<String> inactive = introspect(accessToken, introspectionClientId);

        assertThat(ordinaryClient.statusCode()).isEqualTo(401);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(active.body()).path("active").booleanValue()).isTrue();
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(inactive.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(inactive.body()).path("active").booleanValue()).isFalse();
    }

    @Test
    void identityDisableMakesPreviouslyIssuedUserAuthorizationInactive() throws Exception {
        String introspectionClientId = "ainer-user-introspection-test";
        registeredClientRepository.save(machineClient(
                introspectionClientId, null, true, "token.introspect"));
        ProvisionedIdentity identity = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "introspection-user", "Introspection User", "introspection-user@example.com",
                "strong-password-2026", "Introspection User"));
        RegisteredClient tokenClient = machineClient(
                "ainer-user-token-test", identity.tenantId().toString(), false, "workspace.write");
        registeredClientRepository.save(tokenClient);
        Instant issuedAt = Instant.now().minusSeconds(5);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "user-access-token-" + UUID.randomUUID(),
                issuedAt,
                issuedAt.plus(Duration.ofMinutes(5)),
                Set.of("workspace.write"));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(tokenClient)
                .principalName(identity.subjectId().toString())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("workspace.write"))
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        new LinkedHashMap<>(Map.of(
                                "actor_type", "USER",
                                "sub", identity.subjectId().toString(),
                                "tenant_id", identity.tenantId().toString()))))
                .build();
        authorizationService.save(authorization);

        HttpResponse<String> active = introspect(accessToken.getTokenValue(), introspectionClientId);
        identityAccessLifecycleService.disableUser(identity.subjectId());
        HttpResponse<String> inactive = introspect(accessToken.getTokenValue(), introspectionClientId);

        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(inactive.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(active.body()).path("active").booleanValue()).isTrue();
        assertThat(objectMapper.readTree(inactive.body()).path("active").booleanValue()).isFalse();
    }

    @Test
    void oidcDiscoveryDoesNotAdvertisePasswordGrant() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:%d/.well-known/openid-configuration".formatted(port)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("https://auth.ainer.test")
                .doesNotContain("\"password\"");
    }

    @Test
    void prometheusEndpointRequiresDedicatedTenantlessServiceToken() throws Exception {
        String metricsClientId = "ainer-metrics-test";
        registeredClientRepository.save(machineClient(
                metricsClientId, null, AinerSecurityScopes.PLATFORM_METRICS_READ));
        String tenantMetricsClientId = "ainer-tenant-metrics-test";
        registeredClientRepository.save(machineClient(
                tenantMetricsClientId, TENANT_ID, AinerSecurityScopes.PLATFORM_METRICS_READ));

        HttpResponse<String> unauthenticated = metrics(null);
        HttpResponse<String> tenantBound = metrics(accessToken(
                tenantMetricsClientId, AinerSecurityScopes.PLATFORM_METRICS_READ));
        HttpResponse<String> allowed = metrics(accessToken(
                metricsClientId, AinerSecurityScopes.PLATFORM_METRICS_READ));

        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(tenantBound.statusCode()).isEqualTo(403);
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.body()).contains("# HELP").contains("jvm_");
    }

    @Test
    void directoryEnforcesServiceActorScopeAndTenantBoundary() throws Exception {
        ProvisionedIdentity first = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "directory-first", "Directory First", "first@example.com",
                "strong-password-2026", "First Owner"));
        ProvisionedIdentity second = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "directory-second", "Directory Second", "second@example.com",
                "strong-password-2026", "Second Owner"));
        String boundClientId = "directory-bound-test";
        registeredClientRepository.save(machineClient(
                boundClientId, first.tenantId().toString(), "identity.directory.read"));
        String boundToken = accessToken(boundClientId, "identity.directory.read");

        HttpResponse<String> ownTenant = directoryMember(
                first.tenantId(), first.subjectId(), boundToken);
        HttpResponse<String> otherTenant = directoryMember(
                second.tenantId(), second.subjectId(), boundToken);

        assertThat(ownTenant.statusCode()).isEqualTo(200);
        assertThat(ownTenant.body())
                .contains("\"username\":\"first@example.com\"")
                .contains("\"displayName\":\"First Owner\"")
                .doesNotContain("passwordHash")
                .doesNotContain("clientSecret");
        assertThat(otherTenant.statusCode()).isEqualTo(403);

        String platformClientId = "directory-platform-test";
        registeredClientRepository.save(machineClient(
                platformClientId, null, "identity.directory.read.all"));
        String platformToken = accessToken(platformClientId, "identity.directory.read.all");
        assertThat(directoryMember(second.tenantId(), second.subjectId(), platformToken).statusCode())
                .isEqualTo(200);

        String userToken = userToken(first);
        assertThat(directoryMember(first.tenantId(), first.subjectId(), userToken).statusCode())
                .isEqualTo(403);
    }

    @Test
    void tenantMemberApiUsesUserScopeLiveRoleAndIdentityOwnedDatabase() throws Exception {
        ProvisionedIdentity owner = provision(
                "member-api", "Member API", "owner@member-api.dev", "Member API Owner");
        ProvisionedIdentity target = provision(
                "member-target", "Member Target", "target@member-api.dev", "Member Target");
        ProvisionedIdentity outsider = provision(
                "member-outsider", "Member Outsider", "outsider@member-api.dev", "Member Outsider");
        String ownerToken = activeActorToken(
                owner.subjectId().toString(),
                owner.tenantId().toString(),
                "USER",
                "tenant.members.read tenant.members.write");

        HttpResponse<String> unauthenticated = memberRequest(
                "GET", memberPath(owner, ""), null, null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.body()).contains("AINER.COMMON.UNAUTHENTICATED");

        HttpResponse<String> added = memberRequest(
                "POST",
                memberPath(owner, ""),
                ownerToken,
                """
                        {
                          "username": "TARGET@MEMBER-API.DEV",
                          "role": "MEMBER",
                          "reasonCode": "onboarding"
                        }
                        """);
        assertThat(added.statusCode()).isEqualTo(200);
        assertThat(added.headers().firstValue(RequestIds.HEADER)).isPresent();
        assertThat(added.body())
                .contains("\"code\":\"AINER.COMMON.OK\"")
                .contains(target.subjectId().toString())
                .contains("\"role\":\"MEMBER\"");

        assertForbidden(memberRequest(
                "GET",
                memberPath(owner, ""),
                activeActorToken(
                        target.subjectId().toString(),
                        owner.tenantId().toString(),
                        "USER",
                        "tenant.members.read tenant.members.write"),
                null));
        assertForbidden(memberRequest(
                "GET",
                memberPath(owner, ""),
                activeActorToken(
                        owner.subjectId().toString(),
                        owner.tenantId().toString(),
                        "SERVICE",
                        "tenant.members.read tenant.members.write"),
                null));
        assertForbidden(memberRequest(
                "GET",
                memberPath(owner, ""),
                activeActorToken(
                        outsider.subjectId().toString(),
                        outsider.tenantId().toString(),
                        "USER",
                        "tenant.members.read tenant.members.write"),
                null));

        HttpResponse<String> changed = memberRequest(
                "PATCH",
                memberPath(owner, "/" + target.subjectId()),
                ownerToken,
                """
                        {"role":"ADMIN","reasonCode":"promotion"}
                        """);
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(changed.body()).contains("\"role\":\"ADMIN\"");

        HttpResponse<String> listed = memberRequest(
                "GET", memberPath(owner, "?page=1&size=20"), ownerToken, null);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body())
                .contains("\"total\":2")
                .contains("owner@member-api.dev")
                .contains("target@member-api.dev");

        HttpResponse<String> removed = memberRequest(
                "DELETE",
                memberPath(owner, "/" + target.subjectId() + "?reasonCode=offboarded"),
                ownerToken,
                null);
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_member_audit WHERE tenant_id = ?",
                Integer.class,
                owner.tenantId())).isEqualTo(3);
    }

    @Test
    void ownershipTransferHttpFlowSwapsRolesEndToEnd() throws Exception {
        ProvisionedIdentity owner = provision(
                "ot-http", "OT HTTP", "owner@ot-http.dev", "OT HTTP Owner");
        ProvisionedIdentity admin = provision(
                "ot-admin", "OT Admin Home", "admin@ot-http.dev", "OT HTTP Admin");
        // 直接插入 ADMIN membership（绕过 member API，聚焦 transfer HTTP 链路）
        jdbcTemplate.update(
                "INSERT INTO ainer_identity_membership "
                        + "(tenant_id, user_id, role, is_default, status, joined_at, updated_at) "
                        + "VALUES (?, ?, 'ADMIN', false, 'ACTIVE', NOW(), NOW())",
                owner.tenantId(), admin.subjectId());

        String ownerToken = activeActorToken(
                owner.subjectId().toString(),
                owner.tenantId().toString(),
                "USER",
                "tenant.ownership.transfer tenant.members.write");

        // 发起转移
        String transferPath = "/api/tenants/" + owner.tenantId() + "/ownership-transfers";
        HttpResponse<String> initiated = memberRequest(
                "POST", transferPath, ownerToken,
                """
                {
                  "targetSubjectId": "%s",
                  "reasonCode": "succession"
                }
                """.formatted(admin.subjectId().toString()));
        assertThat(initiated.statusCode()).isEqualTo(200);
        assertThat(initiated.body())
                .contains("\"status\":\"REQUESTED\"")
                .contains(admin.subjectId().toString());
        String transferId = extractJsonField(initiated.body(), "id");

        // 无 scope / SERVICE / 跨 tenant 被拒
        assertForbidden(memberRequest("POST", transferPath, activeActorToken(
                owner.subjectId().toString(), owner.tenantId().toString(),
                "USER", "tenant.members.write"),
                """
                {"targetSubjectId":"%s","reasonCode":"no-scope"}
                """.formatted(admin.subjectId())));
        assertForbidden(memberRequest("POST", transferPath, activeActorToken(
                owner.subjectId().toString(), owner.tenantId().toString(),
                "SERVICE", "tenant.ownership.transfer"),
                """
                {"targetSubjectId":"%s","reasonCode":"service"}
                """.formatted(admin.subjectId())));

        // 目标 ADMIN 接受转移
        String adminToken = activeActorToken(
                admin.subjectId().toString(),
                owner.tenantId().toString(),
                "USER",
                "tenant.ownership.transfer");
        HttpResponse<String> accepted = memberRequest(
                "POST", transferPath + "/" + transferId + "/acceptances",
                adminToken,
                """
                {"reasonCode":"accepted"}
                """);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body())
                .contains("\"status\":\"EXECUTED\"")
                .contains(admin.subjectId().toString());

        // 验证角色交换
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM ainer_identity_membership WHERE tenant_id = ? AND user_id = ?",
                String.class, owner.tenantId(), owner.subjectId())).isEqualTo("ADMIN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM ainer_identity_membership WHERE tenant_id = ? AND user_id = ?",
                String.class, owner.tenantId(), admin.subjectId())).isEqualTo("OWNER");
    }

    @Test
    void ownershipTransferHttpRejectsInitiatorSelfAccept() throws Exception {
        ProvisionedIdentity owner = provision(
                "ot-self", "OT Self", "owner@ot-self.dev", "OT Self Owner");
        ProvisionedIdentity admin = provision(
                "ot-sa", "OT SA Home", "admin@ot-self.dev", "OT Self Admin");
        jdbcTemplate.update(
                "INSERT INTO ainer_identity_membership "
                        + "(tenant_id, user_id, role, is_default, status, joined_at, updated_at) "
                        + "VALUES (?, ?, 'ADMIN', false, 'ACTIVE', NOW(), NOW())",
                owner.tenantId(), admin.subjectId());

        String ownerToken = activeActorToken(
                owner.subjectId().toString(), owner.tenantId().toString(),
                "USER", "tenant.ownership.transfer");
        String transferPath = "/api/tenants/" + owner.tenantId() + "/ownership-transfers";
        HttpResponse<String> initiated = memberRequest(
                "POST", transferPath, ownerToken,
                """
                {"targetSubjectId":"%s","reasonCode":"init"}
                """.formatted(admin.subjectId()));
        String transferId = extractJsonField(initiated.body(), "id");

        // OWNER 尝试自己接受 → 403（角色不是 ADMIN）
        HttpResponse<String> selfAccept = memberRequest(
                "POST", transferPath + "/" + transferId + "/acceptances",
                ownerToken,
                """
                {"reasonCode":"hijack"}
                """);
        assertThat(selfAccept.statusCode()).isEqualTo(403);
    }

    private static String extractJsonField(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Field " + field + " not found in JSON: " + json);
        }
        return matcher.group(1);
    }

    private RegisteredClient machineClient() {
        return machineClient(CLIENT_ID, TENANT_ID, "ai.invoke");
    }

    private RegisteredClient machineClient(String clientId, String tenantId, String... scopes) {
        return machineClient(clientId, tenantId, false, scopes);
    }

    private RegisteredClient machineClient(
            String clientId, String tenantId, boolean introspectionAllowed, String... scopes) {
        return machineClient(clientId, tenantId, null, introspectionAllowed, scopes);
    }

    private RegisteredClient machineClient(
            String clientId, String tenantId, String tokenProfile,
            boolean introspectionAllowed, String... scopes) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                .clientName("Ainer machine integration test")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build());
        for (String scope : scopes) {
            builder.scope(scope);
        }
        ClientSettings.Builder settings = ClientSettings.builder();
        if (tenantId != null) {
            settings.setting(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING, tenantId);
        }
        if (tokenProfile != null) {
            settings.setting(AinerAuthorizationServerConfiguration.TOKEN_PROFILE_SETTING, tokenProfile);
        }
        settings.setting(
                AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING,
                introspectionAllowed);
        return builder.clientSettings(settings.build()).build();
    }

    private HttpResponse<String> token(String secret) throws Exception {
        return token(CLIENT_ID, secret, "ai.invoke");
    }

    private HttpResponse<String> token(String clientId, String secret, String scope) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials&scope="
                + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/oauth2/token".formatted(port)))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken(String clientId, String scope) throws Exception {
        HttpResponse<String> response = token(clientId, CLIENT_SECRET, scope);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).path("access_token").stringValue();
    }

    private HttpResponse<String> introspect(String accessToken, String clientId) throws Exception {
        return protocolPost(
                "/oauth2/introspect",
                "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                clientId);
    }

    private HttpResponse<String> revoke(String accessToken, String clientId) throws Exception {
        return protocolPost(
                "/oauth2/revoke",
                "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                clientId);
    }

    private HttpResponse<String> protocolPost(String path, String form, String clientId) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> directoryMember(
            UUID tenantId,
            UUID subjectId,
            String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/internal/identity/directory/tenants/%s/members/%s"
                        .formatted(port, tenantId, subjectId)))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> internalPost(String path, String accessToken, Object body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return httpClient.send(
                request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> internalGet(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> platformProvisioningPost(
            String accessToken,
            String idempotencyKey,
            Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:%d/internal/platform/identity/"
                                .formatted(port)
                                + "tenant-provisioning-requests"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> metrics(String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/actuator/prometheus".formatted(port)))
                .GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String userToken(ProvisionedIdentity identity) {
        return actorToken(
                identity.subjectId().toString(),
                identity.tenantId().toString(),
                "USER",
                "identity.directory.read");
    }

    private String actorToken(
            String subjectId,
            String tenantId,
            String actorType,
            String scope) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://auth.ainer.test")
                .subject(subjectId)
                .audience(List.of("ainer-api"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("actor_type", actorType)
                .claim("scope", scope);
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId);
        }
        return jwtEncoder.encode(
                        JwtEncoderParameters.from(claims.build()))
                .getTokenValue();
    }

    private String activeActorToken(
            String subjectId,
            String tenantId,
            String actorType,
            String scope) {
        String tokenValue = actorToken(subjectId, tenantId, actorType, scope);
        Jwt jwt = jwtDecoder.decode(tokenValue);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                Set.copyOf(List.of(scope.split(" "))));
        Map<String, Object> authorizationClaims = new LinkedHashMap<>();
        authorizationClaims.put("actor_type", actorType);
        authorizationClaims.put("sub", subjectId);
        authorizationClaims.put("tenant_id", tenantId);
        authorizationClaims.put("scope", scope);
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClientRepository.findByClientId(CLIENT_ID))
                .principalName(subjectId)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(accessToken.getScopes())
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        authorizationClaims))
                .build();
        authorizationService.save(authorization);
        return tokenValue;
    }

    private ProvisionedIdentity provision(
            String tenantCode,
            String tenantName,
            String username,
            String displayName) {
        return identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                tenantCode, tenantName, username, "strong-password-2026", displayName));
    }

    private String memberPath(ProvisionedIdentity owner, String suffix) {
        return "/api/tenants/" + owner.tenantId() + "/members" + suffix;
    }

    private HttpResponse<String> memberRequest(
            String method,
            String path,
            String accessToken,
            String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertForbidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("AINER.COMMON.FORBIDDEN");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestKeyConfiguration {

        @Bean
        JWKSource<SecurityContext> testJwkSource() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("test-key")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        }

        @Bean
        JwtEncoder testJwtEncoder(JWKSource<SecurityContext> testJwkSource) {
            return new NimbusJwtEncoder(testJwkSource);
        }
    }
}
