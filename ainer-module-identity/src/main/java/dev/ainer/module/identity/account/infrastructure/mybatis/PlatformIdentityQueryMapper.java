package dev.ainer.module.identity.account.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PlatformIdentityQueryMapper {

    List<PlatformIdentityTenantRow> selectTenants(
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countTenants();

    List<PlatformIdentityUserRow> selectUsers(
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countUsers();
}
