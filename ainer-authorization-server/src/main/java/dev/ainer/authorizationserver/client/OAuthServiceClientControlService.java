package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.authorizationserver.config.ManagedRegisteredClientRepository;
import dev.ainer.core.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.client-control",
        name = "enabled",
        havingValue = "true")
public class OAuthServiceClientControlService {

    private static final Pattern CLIENT_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,99}");
    private static final Pattern CHANGE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,200}");
    private static final int MAX_CLIENT_NAME_LENGTH = 200;
    private static final int MAX_SCOPES = 16;

    private final ManagedRegisteredClientRepository registeredClients;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OAuthClientControlSettings settings;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public OAuthServiceClientControlService(
            ManagedRegisteredClientRepository registeredClients,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            OAuthClientControlSettings settings,
            SecureRandom secureRandom,
            Clock clock) {
        this.registeredClients = registeredClients;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.settings = settings;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public IssuedClient create(CreateCommand command, OperationActor actor) {
        requireActor(actor);
        requireTenantId(command.tenantId());
        validateClient(command.clientId(), command.clientName(), command.scopes());
        requireChangeReference(command.changeReference());
        if (registeredClients.findIncludingRetiredByClientId(command.clientId()) != null) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        return createRegisteredClient(
                command.clientId(),
                command.clientName(),
                command.tenantId(),
                command.scopes(),
                null,
                command.changeReference(),
                actor,
                "CREATED");
    }

    @Transactional(readOnly = true)
    public ClientView find(String clientId, OperationActor actor) {
        requireActor(actor);
        requireClientId(clientId);
        return loadManagedClient(clientId).view();
    }

    @Transactional
    public IssuedClient rotate(String clientId, RotateCommand command, OperationActor actor) {
        requireActor(actor);
        requireClientId(clientId);
        requireChangeReference(command.changeReference());
        ManagedClient source = loadManagedClient(clientId);
        if (!"ACTIVE".equals(source.profile().status())) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_NOT_ACTIVE);
        }
        String replacementName = command.replacementClientName() == null
                || command.replacementClientName().isBlank()
                ? source.client().getClientName()
                : command.replacementClientName();
        validateClient(command.replacementClientId(), replacementName, source.client().getScopes());
        if (registeredClients.findIncludingRetiredByClientId(command.replacementClientId()) != null) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        return createRegisteredClient(
                command.replacementClientId(),
                replacementName,
                source.profile().tenantId(),
                source.client().getScopes(),
                source.client().getClientId(),
                command.changeReference(),
                actor,
                "ROTATED");
    }

    @Transactional
    public ClientView retire(String clientId, RetireCommand command, OperationActor actor) {
        requireActor(actor);
        requireClientId(clientId);
        requireChangeReference(command.changeReference());
        ManagedClient managed = loadManagedClient(clientId);
        if (!"ACTIVE".equals(managed.profile().status())) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_NOT_ACTIVE);
        }
        Instant now = clock.instant();
        int updated = jdbcTemplate.update(
                "UPDATE ainer_oauth_service_client "
                        + "SET status = 'RETIRED', retired_by_service_id = ?, retired_at = ?, "
                        + "version = version + 1 "
                        + "WHERE client_id = ? AND status = 'ACTIVE' AND version = ?",
                actor.serviceId(),
                databaseTime(now),
                clientId,
                managed.profile().version());
        if (updated != 1) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_STATE_CONFLICT);
        }
        audit(
                "RETIRED",
                clientId,
                null,
                managed.profile().tenantId(),
                actor,
                command.changeReference(),
                now);
        return new ClientView(
                managed.client().getClientId(),
                managed.client().getClientName(),
                managed.profile().tenantId(),
                sortedScopes(managed.client().getScopes()),
                "RETIRED",
                managed.profile().replacesClientId(),
                managed.client().getClientIdIssuedAt(),
                managed.client().getClientSecretExpiresAt(),
                managed.profile().createdAt(),
                now);
    }

    private IssuedClient createRegisteredClient(
            String clientId,
            String clientName,
            UUID tenantId,
            Set<String> scopes,
            String replacesClientId,
            String changeReference,
            OperationActor actor,
            String operation) {
        Instant now = clock.instant();
        String rawSecret = newSecret();
        String registeredClientId = UUID.randomUUID().toString();
        RegisteredClient.Builder builder = RegisteredClient.withId(registeredClientId)
                .clientId(clientId)
                .clientIdIssuedAt(now)
                .clientSecret(passwordEncoder.encode(rawSecret))
                .clientSecretExpiresAt(now.plus(settings.clientSecretTtl()))
                .clientName(clientName)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(
                                AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING,
                                tenantId.toString())
                        .setting(
                                AinerAuthorizationServerConfiguration
                                        .CLIENT_INTROSPECTION_ALLOWED_SETTING,
                                false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(settings.accessTokenTtl())
                        .build());
        scopes.stream().sorted().forEach(builder::scope);
        RegisteredClient client = builder.build();
        try {
            registeredClients.save(client);
            jdbcTemplate.update(
                    "INSERT INTO ainer_oauth_service_client "
                            + "(registered_client_id, client_id, tenant_id, status, "
                            + "replaces_client_id, created_by_service_id, created_at, version) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, 0)",
                    registeredClientId,
                    clientId,
                    tenantId,
                    replacesClientId,
                    actor.serviceId(),
                    databaseTime(now));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        audit(
                operation,
                clientId,
                replacesClientId,
                tenantId,
                actor,
                changeReference,
                now);
        ClientView view = new ClientView(
                clientId,
                clientName,
                tenantId,
                sortedScopes(scopes),
                "ACTIVE",
                replacesClientId,
                client.getClientIdIssuedAt(),
                client.getClientSecretExpiresAt(),
                now,
                null);
        return new IssuedClient(view, rawSecret);
    }

    private ManagedClient loadManagedClient(String clientId) {
        List<ClientProfile> profiles = jdbcTemplate.query(
                "SELECT registered_client_id, client_id, tenant_id, status, replaces_client_id, "
                        + "created_at, retired_at, version "
                        + "FROM ainer_oauth_service_client WHERE client_id = ?",
                (resultSet, rowNum) -> new ClientProfile(
                        resultSet.getString("registered_client_id"),
                        resultSet.getString("client_id"),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getString("replaces_client_id"),
                        instant(resultSet.getObject("created_at", OffsetDateTime.class)),
                        instant(resultSet.getObject("retired_at", OffsetDateTime.class)),
                        resultSet.getLong("version")),
                clientId);
        if (profiles.isEmpty()) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_NOT_FOUND);
        }
        ClientProfile profile = profiles.getFirst();
        RegisteredClient client = registeredClients.findIncludingRetiredByClientId(clientId);
        if (client == null || !profile.registeredClientId().equals(client.getId())) {
            throw new BusinessException(OAuthClientControlErrorCode.CLIENT_STATE_CONFLICT);
        }
        return new ManagedClient(client, profile);
    }

    private void audit(
            String operation,
            String clientId,
            String relatedClientId,
            UUID tenantId,
            OperationActor actor,
            String changeReference,
            Instant occurredAt) {
        jdbcTemplate.update(
                "INSERT INTO ainer_oauth_service_client_audit "
                        + "(id, operation, client_id, related_client_id, tenant_id, "
                        + "actor_service_id, request_id, change_reference, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                operation,
                clientId,
                relatedClientId,
                tenantId,
                actor.serviceId(),
                actor.requestId(),
                changeReference,
                databaseTime(occurredAt));
    }

    private void validateClient(String clientId, String clientName, Set<String> scopes) {
        requireClientId(clientId);
        if (clientName == null || clientName.isBlank() || clientName.length() > MAX_CLIENT_NAME_LENGTH) {
            throw new BusinessException(OAuthClientControlErrorCode.INVALID_REQUEST);
        }
        if (scopes == null || scopes.isEmpty() || scopes.size() > MAX_SCOPES) {
            throw new BusinessException(OAuthClientControlErrorCode.INVALID_REQUEST);
        }
        if (!settings.allowedScopes().containsAll(scopes)) {
            throw new BusinessException(OAuthClientControlErrorCode.SCOPE_NOT_ALLOWED);
        }
    }

    private void requireClientId(String clientId) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw new BusinessException(OAuthClientControlErrorCode.INVALID_REQUEST);
        }
    }

    private void requireChangeReference(String changeReference) {
        if (changeReference == null || !CHANGE_REFERENCE.matcher(changeReference).matches()) {
            throw new BusinessException(OAuthClientControlErrorCode.INVALID_REQUEST);
        }
    }

    private void requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(OAuthClientControlErrorCode.INVALID_REQUEST);
        }
    }

    private void requireActor(OperationActor actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.tenantId() != null
                || !settings.operatorClientIds().contains(actor.serviceId())
                || actor.requestId() == null
                || actor.requestId().isBlank()) {
            throw new BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
        }
    }

    private String newSecret() {
        byte[] bytes = new byte[settings.secretBytes()];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private List<String> sortedScopes(Set<String> scopes) {
        return scopes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public record CreateCommand(
            String clientId,
            String clientName,
            UUID tenantId,
            Set<String> scopes,
            String changeReference) {

        public CreateCommand {
            scopes = scopes == null ? null : Set.copyOf(scopes);
        }
    }

    public record RotateCommand(
            String replacementClientId,
            String replacementClientName,
            String changeReference) {
    }

    public record RetireCommand(String changeReference) {
    }

    public record OperationActor(String serviceId, String tenantId, String requestId) {
    }

    public record ClientView(
            String clientId,
            String clientName,
            UUID tenantId,
            List<String> scopes,
            String status,
            String replacesClientId,
            Instant clientIdIssuedAt,
            Instant clientSecretExpiresAt,
            Instant createdAt,
            Instant retiredAt) {
    }

    public static final class IssuedClient {

        private final ClientView client;
        private final String clientSecret;

        IssuedClient(ClientView client, String clientSecret) {
            this.client = client;
            this.clientSecret = clientSecret;
        }

        public ClientView getClient() {
            return client;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        @Override
        public String toString() {
            return "IssuedClient[client=%s, clientSecret=[REDACTED]]".formatted(client);
        }
    }

    private record ClientProfile(
            String registeredClientId,
            String clientId,
            UUID tenantId,
            String status,
            String replacesClientId,
            Instant createdAt,
            Instant retiredAt,
            long version) {
    }

    private record ManagedClient(RegisteredClient client, ClientProfile profile) {

        ClientView view() {
            return new ClientView(
                    client.getClientId(),
                    client.getClientName(),
                    profile.tenantId(),
                    client.getScopes().stream().sorted().toList(),
                    profile.status(),
                    profile.replacesClientId(),
                    client.getClientIdIssuedAt(),
                    client.getClientSecretExpiresAt(),
                    profile.createdAt(),
                    profile.retiredAt());
        }
    }
}
