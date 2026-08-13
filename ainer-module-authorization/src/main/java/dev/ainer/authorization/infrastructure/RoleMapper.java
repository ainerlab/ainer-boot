package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper for the role aggregate and its permission association (ADR-0030 S1).
 */
@Mapper
public interface RoleMapper {

    /**
     * Insert a role row and return the database-generated UUIDv7 primary key.
     * Uses {@code INSERT ... RETURNING id} so must be a {@code <select>} (MyBatis disallows non-int
     * return types on {@code <insert>}).
     */
    UUID insertReturningId(@Param("row") RoleRow row, @Param("now") java.time.Instant now);

    RoleRow selectById(@Param("id") UUID id);

    RoleRow selectActiveByCode(@Param("code") String code);

    List<RoleRow> selectAll();

    /**
     * Delete all permission associations for a role.
     */
    int deletePermissions(@Param("roleId") UUID roleId);

    /**
     * Insert a batch of permission codes for a role.
     */
    int insertPermissions(@Param("roleId") UUID roleId, @Param("codes") List<String> permissionCodes,
                          @Param("now") java.time.Instant now);

    /**
     * Bump the role version, guarded by an optimistic version check.
     * Returns the number of affected rows (0 if the version is stale).
     */
    int bumpVersion(@Param("roleId") UUID roleId, @Param("expectedVersion") long expectedVersion,
                    @Param("now") java.time.Instant now);

    List<String> selectPermissionCodes(@Param("roleId") UUID roleId);
}
