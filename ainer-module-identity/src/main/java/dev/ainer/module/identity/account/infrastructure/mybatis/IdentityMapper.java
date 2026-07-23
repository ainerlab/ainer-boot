package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityMapper {

    int insertTenant(IdentityTenant tenant);

    int insertUser(IdentityUser user);

    int insertMembership(TenantMembership membership);

    IdentityAccountRow selectAccountByUsername(@Param("username") String username);

    IdentityDirectoryRow selectActiveDirectoryEntry(
            @Param("tenantId") UUID tenantId,
            @Param("subjectId") UUID subjectId);

    List<IdentityDirectoryRow> searchActiveDirectory(
            @Param("tenantId") UUID tenantId,
            @Param("likePattern") String likePattern,
            @Param("limit") int limit);

    IdentityTokenStatusRow selectTokenStatus(
            @Param("tenantId") UUID tenantId,
            @Param("subjectId") UUID subjectId);

    String selectUserStatusForUpdate(@Param("subjectId") UUID subjectId);

    List<UUID> selectActiveMembershipTenantIds(@Param("subjectId") UUID subjectId);

    int updateUserStatus(
            @Param("subjectId") UUID subjectId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("updatedAt") Instant updatedAt);

    IdentityMembershipRow selectMembershipForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("subjectId") UUID subjectId);

    int updateMembershipStatus(
            @Param("tenantId") UUID tenantId,
            @Param("subjectId") UUID subjectId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("updatedAt") Instant updatedAt);

    int insertAccessEvent(IdentityAccessEventRow event);
}
