package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ServicePrincipalRepository} 的 PostgreSQL 实现（Greenfield foundation 持久化，
 * S1.1 主干）。沿用项目统一的纯 MyBatis 仓库风格：委托给
 * {@link ServicePrincipalMapper}，通过 {@code uuidv7()} 生成主键，插入未精确影响一行时
 * 立即失败。
 */
@Repository
public class MybatisServicePrincipalRepository implements ServicePrincipalRepository {

    private final ServicePrincipalMapper mapper;

    public MybatisServicePrincipalRepository(ServicePrincipalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void save(ServicePrincipal principal) {
        ServicePrincipalRow row = new ServicePrincipalRow();
        row.setId(principal.principalId());
        row.setIssuer(principal.authority().issuer());
        row.setRealm(principal.authority().realm());
        row.setStatus(principal.status().name());
        row.setSecurityEpoch(principal.securityEpoch());
        row.setCreatedAt(principal.createdAt());
        if (mapper.insertPrincipal(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert ServicePrincipal " + principal.principalId());
        }
    }

    @Override
    public Optional<ServicePrincipal> findByPrincipalId(UUID principalId) {
        return Optional.ofNullable(mapper.selectByPrincipalId(principalId))
                .map(MybatisServicePrincipalRepository::toPrincipal);
    }

    @Override
    public Optional<ServicePrincipal> findByActiveClientId(String clientId) {
        return Optional.ofNullable(mapper.selectByActiveClientId(clientId))
                .map(MybatisServicePrincipalRepository::toPrincipal);
    }

    private static ServicePrincipal toPrincipal(ServicePrincipalRow row) {
        return new ServicePrincipal(
                row.getId(),
                new IdentityAuthorityRef(row.getIssuer(), row.getRealm()),
                ServicePrincipalStatus.valueOf(row.getStatus()),
                row.getSecurityEpoch(),
                row.getCreatedAt());
    }
}
