package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.PlatformIdentityQueryRepository;
import dev.ainer.module.identity.account.application.PlatformIdentityTenantProjection;
import dev.ainer.module.identity.account.application.PlatformIdentityUserProjection;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisPlatformIdentityQueryRepository
        implements PlatformIdentityQueryRepository {

    private final PlatformIdentityQueryMapper mapper;

    public MybatisPlatformIdentityQueryRepository(
            PlatformIdentityQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PlatformIdentityTenantProjection> findTenants(long offset, int limit) {
        return mapper.selectTenants(offset, limit).stream()
                .map(row -> new PlatformIdentityTenantProjection(
                        row.getId(),
                        row.getCode(),
                        row.getName(),
                        IdentityStatus.valueOf(row.getStatus()),
                        row.getCreatedAt(),
                        row.getUpdatedAt()))
                .toList();
    }

    @Override
    public long countTenants() {
        return mapper.countTenants();
    }

    @Override
    public List<PlatformIdentityUserProjection> findUsers(long offset, int limit) {
        return mapper.selectUsers(offset, limit).stream()
                .map(row -> new PlatformIdentityUserProjection(
                        row.getSubjectId(),
                        row.getUsername(),
                        row.getDisplayName(),
                        IdentityStatus.valueOf(row.getStatus()),
                        row.getCreatedAt(),
                        row.getUpdatedAt()))
                .toList();
    }

    @Override
    public long countUsers() {
        return mapper.countUsers();
    }
}
