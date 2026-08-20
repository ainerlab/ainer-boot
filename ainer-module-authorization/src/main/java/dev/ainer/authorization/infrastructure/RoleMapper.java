package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Role 聚合及其权限关联的 MyBatis mapper（ADR-0030 S1）。
 */
@Mapper
public interface RoleMapper {

    /**
     * 插入 Role 行并返回数据库生成的 UUIDv7 主键。使用
     * {@code INSERT ... RETURNING id}，因此必须是 {@code <select>}（MyBatis 不允许
     * {@code <insert>} 返回非 int 类型）。
     */
    UUID insertReturningId(@Param("row") RoleRow row, @Param("now") java.time.Instant now);

    RoleRow selectById(@Param("id") UUID id);

    RoleRow selectActiveByCode(@Param("code") String code);

    List<RoleRow> selectAll();

    /**
     * 删除 Role 的全部权限关联。
     */
    int deletePermissions(@Param("roleId") UUID roleId);

    /**
     * 为 Role 批量插入权限 code。
     */
    int insertPermissions(@Param("roleId") UUID roleId, @Param("codes") List<String> permissionCodes,
                          @Param("now") java.time.Instant now);

    /**
     * 递增 Role 版本，由乐观版本检查守护。返回受影响行数（版本过期时为 0）。
     */
    int bumpVersion(@Param("roleId") UUID roleId, @Param("expectedVersion") long expectedVersion,
                    @Param("now") java.time.Instant now);

    List<String> selectPermissionCodes(@Param("roleId") UUID roleId);
}
