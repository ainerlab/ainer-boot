package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link OAuthClientBindingRepository} 的 PostgreSQL 实现（Greenfield foundation 持久化，
 * S1.1 主干）。沿用项目统一的纯 MyBatis 仓库风格：委托给
 * {@link OAuthClientBindingMapper}，通过 {@code uuidv7()} 生成主键，插入未精确影响一行时
 * 立即失败。
 */
@Repository
public class MybatisOAuthClientBindingRepository implements OAuthClientBindingRepository {

    private final OAuthClientBindingMapper mapper;

    public MybatisOAuthClientBindingRepository(OAuthClientBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void save(OAuthClientBinding binding) {
        OAuthClientBindingRow row = new OAuthClientBindingRow();
        row.setId(binding.bindingId());
        row.setPrincipalId(binding.principalId());
        row.setClientId(binding.clientId());
        row.setStatus(binding.status().name());
        row.setBoundAt(binding.boundAt());
        row.setUnboundAt(binding.unboundAt());
        if (mapper.insertBinding(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert OAuthClientBinding " + binding.bindingId());
        }
    }

    @Override
    public Optional<OAuthClientBinding> findActiveByClientId(String clientId) {
        return Optional.ofNullable(mapper.selectActiveByClientId(clientId))
                .map(MybatisOAuthClientBindingRepository::toBinding);
    }

    @Override
    public Optional<OAuthClientBinding> findByPrincipalId(UUID principalId) {
        return Optional.ofNullable(mapper.selectByPrincipalId(principalId))
                .map(MybatisOAuthClientBindingRepository::toBinding);
    }

    private static OAuthClientBinding toBinding(OAuthClientBindingRow row) {
        return new OAuthClientBinding(
                row.getId(),
                row.getPrincipalId(),
                row.getClientId(),
                OAuthClientBindingStatus.valueOf(row.getStatus()),
                row.getBoundAt(),
                row.getUnboundAt());
    }
}
