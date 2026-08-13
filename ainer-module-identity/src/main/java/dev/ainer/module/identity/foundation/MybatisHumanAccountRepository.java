package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link HumanAccountRepository} (Greenfield foundation persistence, S1.2).
 * Mirrors the project's plain-MyBatis repository style: delegates to {@link HumanAccountMapper}, generates
 * primary keys via {@code uuidv7()} and fails loudly when an insert does not affect exactly one row.
 */
@Repository
public class MybatisHumanAccountRepository implements HumanAccountRepository {

    private final HumanAccountMapper mapper;

    public MybatisHumanAccountRepository(HumanAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void save(HumanAccount account) {
        HumanAccountRow row = new HumanAccountRow();
        row.setId(account.accountId());
        row.setIssuer(account.authority().issuer());
        row.setRealm(account.authority().realm());
        row.setStatus(account.status().name());
        row.setSecurityEpoch(account.securityEpoch());
        row.setCreatedAt(account.createdAt());
        if (mapper.insertAccount(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert HumanAccount " + account.accountId());
        }
    }

    @Override
    public Optional<HumanAccount> findByAccountId(UUID accountId) {
        return Optional.ofNullable(mapper.selectByAccountId(accountId))
                .map(MybatisHumanAccountRepository::toAccount);
    }

    private static HumanAccount toAccount(HumanAccountRow row) {
        return new HumanAccount(
                row.getId(),
                new IdentityAuthorityRef(row.getIssuer(), row.getRealm()),
                AccountStatus.valueOf(row.getStatus()),
                row.getSecurityEpoch(),
                row.getCreatedAt());
    }
}
