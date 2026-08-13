package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link ServicePrincipalRepository} (Greenfield foundation persistence, S1.1 spine).
 * Mirrors the project's plain-MyBatis repository style: delegates to {@link ServicePrincipalMapper}, generates
 * primary keys via {@code uuidv7()} and fails loudly when an insert does not affect exactly one row.
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
