package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link OAuthClientBindingRepository} (Greenfield foundation persistence, S1.1 spine).
 * Mirrors the project's plain-MyBatis repository style: delegates to {@link OAuthClientBindingMapper},
 * generates primary keys via {@code uuidv7()} and fails loudly when an insert does not affect exactly one row.
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
