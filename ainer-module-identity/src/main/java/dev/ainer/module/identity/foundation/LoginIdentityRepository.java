package dev.ainer.module.identity.foundation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link LoginIdentity} bindings (ADR-0033 Greenfield §4).
 *
 * <p>The login lookup key is {@code (type, providerAuthority, normalizedIdentifier)}: authentication resolves
 * a credential to at most one binding, which in turn references exactly one {@link HumanAccount}. Equal raw
 * identifiers under different providers / authorities are distinct bindings and never collapse.
 */
public interface LoginIdentityRepository {

    void save(LoginIdentity identity);

    Optional<LoginIdentity> findByTypeAndIdentifier(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier);

    List<LoginIdentity> findByAccount(UUID accountId);
}
