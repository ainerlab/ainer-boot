package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link LoginIdentityRepository} 的 PostgreSQL 实现（Greenfield foundation 持久化，S1.2）。
 * {@code findByTypeAndIdentifier} 解析凭证的唯一 ACTIVE 绑定；REVOKED 绑定不阻塞
 * 重新绑定，与表上的部分唯一索引语义一致。
 */
@Repository
public class MybatisLoginIdentityRepository implements LoginIdentityRepository {

    private final LoginIdentityMapper mapper;

    public MybatisLoginIdentityRepository(LoginIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(LoginIdentity identity) {
        LoginIdentityRow row = toRow(identity);
        if (mapper.insertLogin(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert LoginIdentity " + identity.identityId());
        }
    }

    @Override
    public Optional<LoginIdentity> findByTypeAndIdentifier(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        return Optional.ofNullable(mapper.selectByTypeAndIdentifier(
                        type.name(), providerAuthority, normalizedIdentifier))
                .map(MybatisLoginIdentityRepository::toLogin);
    }

    @Override
    public List<LoginIdentity> findByAccount(UUID accountId) {
        return mapper.selectByAccount(accountId).stream()
                .map(MybatisLoginIdentityRepository::toLogin)
                .toList();
    }

    private static LoginIdentityRow toRow(LoginIdentity identity) {
        LoginIdentityRow row = new LoginIdentityRow();
        row.setId(identity.identityId());
        row.setAccountId(identity.accountId());
        row.setType(identity.type().name());
        row.setProviderAuthority(identity.providerAuthority());
        row.setNormalizedIdentifier(identity.normalizedIdentifier());
        row.setStatus(identity.status().name());
        row.setVerifiedAt(identity.verifiedAt());
        row.setLinkedAt(identity.linkedAt());
        row.setLastUsedAt(identity.lastUsedAt());
        return row;
    }

    private static LoginIdentity toLogin(LoginIdentityRow row) {
        return new LoginIdentity(
                row.getId(),
                row.getAccountId(),
                LoginIdentityType.valueOf(row.getType()),
                row.getProviderAuthority(),
                row.getNormalizedIdentifier(),
                LoginIdentityStatus.valueOf(row.getStatus()),
                row.getVerifiedAt(),
                row.getLinkedAt(),
                row.getLastUsedAt());
    }
}
