package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link HumanProfileRepository} (Greenfield foundation persistence, S2).
 * Mirrors the project's plain-MyBatis repository style. A profile is a 0:1 aggregate: upsert inserts when
 * absent and updates when present; both fail loudly (through the mapper) if the row is not affected.
 */
@Repository
public class MybatisHumanProfileRepository implements HumanProfileRepository {

    private final HumanProfileMapper mapper;

    public MybatisHumanProfileRepository(HumanProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<HumanProfile> findByAccountId(UUID accountId) {
        return Optional.ofNullable(mapper.selectByAccountId(accountId))
                .map(MybatisHumanProfileRepository::toProfile);
    }

    @Override
    public void upsert(HumanProfile profile) {
        HumanProfileRow row = toRow(profile);
        if (mapper.updateProfile(row) == 1) {
            return;
        }
        if (mapper.insertProfile(row) != 1) {
            throw new IllegalStateException(
                    "failed to upsert HumanProfile for account " + profile.accountId());
        }
    }

    @Override
    public void update(HumanProfile profile) {
        HumanProfileRow row = toRow(profile);
        if (mapper.updateProfile(row) != 1) {
            throw new IllegalStateException(
                    "failed to update HumanProfile for account " + profile.accountId());
        }
    }

    private static HumanProfile toProfile(HumanProfileRow row) {
        return new HumanProfile(
                row.getAccountId(),
                row.getDisplayName(),
                row.getAvatarUrl(),
                row.getUpdatedAt());
    }

    private static HumanProfileRow toRow(HumanProfile profile) {
        HumanProfileRow row = new HumanProfileRow();
        row.setAccountId(profile.accountId());
        row.setDisplayName(profile.displayName());
        row.setAvatarUrl(profile.avatarUrl());
        row.setUpdatedAt(profile.updatedAt());
        return row;
    }
}