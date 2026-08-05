package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link HumanAccount} (ADR-0033 Greenfield §3).
 *
 * <p>Production implements this against the rebuilt Identity baseline (PostgreSQL UUIDv7 primary keys,
 * authority-qualified lookup). The Greenfield skeleton provides an in-memory implementation so the
 * registration / login-lookup flow is exercisable before the database cutover. Identity does not expose
 * raw storage; callers consume the typed aggregate only.
 */
public interface HumanAccountRepository {

    void save(HumanAccount account);

    Optional<HumanAccount> findByAccountId(UUID accountId);
}
