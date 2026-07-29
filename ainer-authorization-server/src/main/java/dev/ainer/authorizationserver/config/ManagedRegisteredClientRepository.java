package dev.ainer.authorizationserver.config;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Objects;

/**
 * Keeps Spring Authorization Server's JDBC schema as the protocol source of truth while
 * enforcing Ainer-owned lifecycle state for managed service clients.
 */
public final class ManagedRegisteredClientRepository implements RegisteredClientRepository {

    private static final String ACTIVE = "ACTIVE";

    private final JdbcRegisteredClientRepository delegate;
    private final JdbcTemplate jdbcTemplate;

    public ManagedRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.delegate = new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        // Historical authorizations must still be reconstructable after client retirement.
        return delegate.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient client = delegate.findByClientId(clientId);
        return isActive(client) ? client : null;
    }

    public RegisteredClient findIncludingRetiredByClientId(String clientId) {
        return delegate.findByClientId(clientId);
    }

    public boolean isActiveByRegisteredClientId(String registeredClientId) {
        if (registeredClientId == null || registeredClientId.isBlank()) {
            return false;
        }
        return lifecycleAllows(registeredClientId);
    }

    private boolean isActive(RegisteredClient client) {
        if (client == null) {
            return false;
        }
        return lifecycleAllows(client.getId());
    }

    private boolean lifecycleAllows(String registeredClientId) {
        String status = queryLifecycleStatus(registeredClientId);
        return status == null || ACTIVE.equals(status);
    }

    private String queryLifecycleStatus(String registeredClientId) {
        String serviceStatus = queryStatus(
                "SELECT status FROM ainer_oauth_service_client WHERE registered_client_id = ?",
                registeredClientId);
        if (serviceStatus != null) {
            return serviceStatus;
        }
        return queryStatus(
                "SELECT status FROM ainer_oauth_browser_client WHERE registered_client_id = ?",
                registeredClientId);
    }

    private String queryStatus(String sql, String registeredClientId) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, registeredClientId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
