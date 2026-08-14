package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.authorizationserver.config.ManagedRegisteredClientRepository;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.browser-client-control",
        name = "enabled",
        havingValue = "true")
public class OAuthBrowserClientControlService {

    private static final String ACTIVE = "ACTIVE";
    private static final String RETIRED = "RETIRED";

    private final ManagedRegisteredClientRepository registeredClients;
    private final JdbcTemplate jdbcTemplate;
    private final OAuthBrowserClientControlConfiguration config;
    private final Clock clock;

    public OAuthBrowserClientControlService(
            ManagedRegisteredClientRepository registeredClients,
            JdbcTemplate jdbcTemplate,
            OAuthBrowserClientControlConfiguration config,
            Clock clock) {
        this.registeredClients = registeredClients;
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.clock = clock;
    }

    @Transactional
    public BrowserClientView create(CreateCommand command, OperationActor actor) {
        requireActor(actor);
        requireClientId(command.clientId());
        requireText(command.clientName(), "client name", 200);
        requireRedirectUri(command.redirectUri());
        requireRedirectUri(command.postLogoutRedirectUri());
        requireSameOrigin(command.redirectUri(), command.postLogoutRedirectUri());
        config.validateRequestedScopes(command.scopes());

        if (registeredClients.findIncludingRetiredByClientId(command.clientId()) != null) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        Instant now = clock.instant();
        RegisteredClient client = buildBrowserClient(
                command.clientId(), command.clientName(),
                command.redirectUri(), command.postLogoutRedirectUri(),
                command.scopes());
        try {
            registeredClients.save(client);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        insertLifecycle(client.getId(), command.clientId(), command.clientName(),
                null, actor, now);
        audit("CREATED", command.clientId(), null, actor, command.changeReference(), now);
        return findInternal(command.clientId());
    }

    @Transactional(readOnly = true)
    public BrowserClientView find(String clientId, OperationActor actor) {
        requireActor(actor);
        return findInternal(clientId);
    }

    @Transactional(readOnly = true)
    public BrowserClientPage list(int page, int size, OperationActor actor) {
        requireActor(actor);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
        }
        long offset = (long) (page - 1) * size;
        int total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_oauth_browser_client", Integer.class);
        List<BrowserClientView> clients = jdbcTemplate.queryForStream(
                "SELECT registered_client_id, client_id, client_name, status, replaces_client_id, "
                        + "created_at, retired_at, version "
                        + "FROM ainer_oauth_browser_client "
                        + "ORDER BY created_at DESC, client_id "
                        + "LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    BrowserClientRow row = new BrowserClientRow(
                            rs.getString("registered_client_id"),
                            rs.getString("client_id"),
                            rs.getString("client_name"),
                            rs.getString("status"),
                            rs.getString("replaces_client_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("retired_at") != null
                                    ? rs.getTimestamp("retired_at").toInstant() : null,
                            rs.getLong("version"));
                    RegisteredClient rc = registeredClients.findIncludingRetiredByClientId(row.clientId());
                    return toView(row, rc);
                },
                size, offset).toList();
        return new BrowserClientPage(clients, page, size, total);
    }

    @Transactional
    public BrowserClientView rotate(String clientId, RotateCommand command, OperationActor actor) {
        requireActor(actor);
        requireClientId(command.replacementClientId());
        requireText(command.replacementClientName(), "replacement client name", 200);
        requireRedirectUri(command.redirectUri());
        requireRedirectUri(command.postLogoutRedirectUri());
        requireSameOrigin(command.redirectUri(), command.postLogoutRedirectUri());

        BrowserClientRow source = loadActiveClient(clientId);
        RegisteredClient sourceClient = registeredClients.findIncludingRetiredByClientId(clientId);
        if (sourceClient == null) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_STATE_CONFLICT);
        }
        config.validateRequestedScopes(sourceClient.getScopes());

        if (registeredClients.findIncludingRetiredByClientId(command.replacementClientId()) != null) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        Instant now = clock.instant();
        RegisteredClient replacement = buildBrowserClient(
                command.replacementClientId(), command.replacementClientName(),
                command.redirectUri(), command.postLogoutRedirectUri(),
                sourceClient.getScopes());
        try {
            registeredClients.save(replacement);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
        insertLifecycle(replacement.getId(), command.replacementClientId(),
                command.replacementClientName(), clientId, actor, now);
        audit("ROTATED", command.replacementClientId(), clientId,
                actor, command.changeReference(), now);
        return findInternal(command.replacementClientId());
    }

    @Transactional
    public BrowserClientView retire(String clientId, RetireCommand command, OperationActor actor) {
        requireActor(actor);
        BrowserClientRow managed = loadActiveClient(clientId);
        Instant now = clock.instant();
        int updated = jdbcTemplate.update(
                "UPDATE ainer_oauth_browser_client "
                        + "SET status = 'RETIRED', retired_by_service_id = ?, retired_at = ?, "
                        + "version = version + 1 "
                        + "WHERE client_id = ? AND status = 'ACTIVE' AND version = ?",
                actor.serviceId(), java.sql.Timestamp.from(now), clientId, managed.version());
        if (updated != 1) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_STATE_CONFLICT);
        }
        audit("RETIRED", clientId, null, actor, command.changeReference(), now);
        return findInternal(clientId);
    }

    private RegisteredClient buildBrowserClient(
            String clientId, String clientName,
            String redirectUri, String postLogoutRedirectUri,
            Set<String> scopes) {
        return RegisteredClient.withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(clientId)
                .clientName(clientName)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(postLogoutRedirectUri)
                .scopes(s -> s.addAll(scopes))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(config.defaultAccessTokenMinutes()))
                        .build())
                .build();
    }

    private BrowserClientView findInternal(String clientId) {
        BrowserClientRow row = loadRow(clientId);
        RegisteredClient client = registeredClients.findIncludingRetiredByClientId(clientId);
        return toView(row, client);
    }

    private BrowserClientRow loadActiveClient(String clientId) {
        BrowserClientRow row = loadRow(clientId);
        if (!ACTIVE.equals(row.status())) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_STATE_CONFLICT);
        }
        return row;
    }

    private BrowserClientRow loadRow(String clientId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT registered_client_id, client_id, client_name, status, replaces_client_id, "
                            + "created_at, retired_at, version "
                            + "FROM ainer_oauth_browser_client WHERE client_id = ?",
                    (rs, rowNum) -> new BrowserClientRow(
                            rs.getString("registered_client_id"),
                            rs.getString("client_id"),
                            rs.getString("client_name"),
                            rs.getString("status"),
                            rs.getString("replaces_client_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("retired_at") != null
                                    ? rs.getTimestamp("retired_at").toInstant() : null,
                            rs.getLong("version")),
                    clientId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_NOT_FOUND);
        }
    }

    private void insertLifecycle(
            String registeredClientId, String clientId, String clientName,
            String replacesClientId, OperationActor actor, Instant now) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ainer_oauth_browser_client "
                            + "(registered_client_id, client_id, client_name, status, replaces_client_id, "
                            + "created_by_service_id, created_at, version) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, 0)",
                    registeredClientId, clientId, clientName, replacesClientId,
                    actor.serviceId(), java.sql.Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.CLIENT_ALREADY_EXISTS);
        }
    }

    private void audit(String operation, String clientId, String relatedClientId,
                       OperationActor actor, String changeReference, Instant occurredAt) {
        jdbcTemplate.update(
                "INSERT INTO ainer_oauth_browser_client_audit "
                        + "(id, operation, client_id, related_client_id, actor_service_id, "
                        + "request_id, change_reference, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                dev.ainer.core.uuid.Uuidv7.generate(), operation, clientId, relatedClientId,
                actor.serviceId(), actor.requestId(), changeReference,
                java.sql.Timestamp.from(occurredAt));
    }

    private void requireActor(OperationActor actor) {
        Objects.requireNonNull(actor, "actor");
        if (!config.operatorClientIds().contains(actor.serviceId())
                || actor.requestId() == null || actor.requestId().isBlank()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireClientId(String clientId) {
        if (clientId == null || !clientId.matches("[a-z0-9][a-z0-9._-]{2,99}")) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
        }
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
        }
    }

    private static void requireRedirectUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost());
            if (!uri.isAbsolute() || (!secure && !loopbackHttp) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
        }
    }

    private static void requireSameOrigin(String left, String right) {
        URI l = URI.create(left);
        URI r = URI.create(right);
        if (!l.getScheme().equalsIgnoreCase(r.getScheme())
                || !l.getHost().equalsIgnoreCase(r.getHost())
                || effectivePort(l) != effectivePort(r)) {
            throw new BusinessException(OAuthBrowserClientControlErrorCode.INVALID_CLIENT_REQUEST);
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort()
                : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static BrowserClientView toView(BrowserClientRow row, RegisteredClient client) {
        Set<String> scopes = client != null ? new HashSet<>(client.getScopes()) : Set.of();
        return new BrowserClientView(
                row.clientId(), row.clientName(), scopes, row.status(),
                row.replacesClientId(), row.createdAt(), row.retiredAt());
    }

    public record CreateCommand(
            String clientId, String clientName,
            String redirectUri, String postLogoutRedirectUri,
            Set<String> scopes, String changeReference) {}

    public record RotateCommand(
            String replacementClientId, String replacementClientName,
            String redirectUri, String postLogoutRedirectUri,
            String changeReference) {}

    public record RetireCommand(String changeReference) {}

    public record OperationActor(String serviceId, String requestId) {}

    public record BrowserClientRow(
            String registeredClientId, String clientId, String clientName,
            String status, String replacesClientId,
            Instant createdAt, Instant retiredAt, long version) {}

    public record BrowserClientView(
            String clientId, String clientName, Set<String> scopes,
            String status, String replacesClientId,
            Instant createdAt, Instant retiredAt) {}

    public record BrowserClientPage(
            List<BrowserClientView> clients, int page, int size, int total) {}
}
