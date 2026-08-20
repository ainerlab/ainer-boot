package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link HumanProfileRepository} 的 PostgreSQL 实现（Greenfield foundation 持久化，S2）。
 * 沿用项目统一的纯 MyBatis 仓库风格。档案是 0:1 聚合：upsert 在缺失时插入、存在时更新；
 * 两种路径（经 mapper）在未影响行时都会立即失败。
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