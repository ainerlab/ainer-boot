package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CredentialRepository} 的 PostgreSQL 实现（Greenfield foundation 持久化，S2）。
 * 沿用项目统一的纯 MyBatis 仓库风格：委托给 {@link CredentialMapper}，通过
 * {@code uuidv7()} 生成主键，插入未精确影响一行时立即失败。
 * {@link #revokeActive} 返回是否确有 ACTIVE 材料被取代。
 */
@Repository
public class MybatisCredentialRepository implements CredentialRepository {

    private final CredentialMapper mapper;

    public MybatisCredentialRepository(CredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void insert(Credential credential) {
        CredentialRow row = new CredentialRow();
        row.setId(credential.credentialId());
        row.setAccountId(credential.accountId());
        row.setType(credential.type().name());
        row.setCredentialData(credential.credentialData());
        row.setStatus(credential.status().name());
        row.setCreatedAt(credential.createdAt());
        if (mapper.insertCredential(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert Credential " + credential.credentialId());
        }
    }

    @Override
    public Optional<Credential> findActive(UUID accountId, CredentialType type) {
        return Optional.ofNullable(mapper.selectActiveByAccountAndType(accountId, type.name()))
                .map(MybatisCredentialRepository::toCredential);
    }

    @Override
    public int revokeActive(UUID accountId, CredentialType type, Instant rotatedAt) {
        return mapper.revokeActiveByAccountAndType(accountId, type.name(), rotatedAt);
    }

    private static Credential toCredential(CredentialRow row) {
        return new Credential(
                row.getId(),
                row.getAccountId(),
                CredentialType.valueOf(row.getType()),
                row.getCredentialData(),
                CredentialStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                row.getRotatedAt());
    }
}