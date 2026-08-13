package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link HumanProfile} (ADR-0033 Greenfield §4, execution plan 缺口 A).
 *
 * <p>A 0:1 aggregate: read by account; upsert replaces or inserts the single profile row for the account.
 * The port exposes only the typed aggregate; no operator can create a profile without an account.
 */
public interface HumanProfileRepository {

    Optional<HumanProfile> findByAccountId(UUID accountId);

    void upsert(HumanProfile profile);

    void update(HumanProfile profile);
}