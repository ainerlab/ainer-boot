package dev.ainer.module.identity.account.application;

import java.util.List;

public interface PlatformIdentityQueryRepository {

    List<PlatformIdentityTenantProjection> findTenants(long offset, int limit);

    long countTenants();

    List<PlatformIdentityUserProjection> findUsers(long offset, int limit);

    long countUsers();
}
