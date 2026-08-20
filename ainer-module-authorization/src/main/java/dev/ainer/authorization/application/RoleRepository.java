package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Role;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Role 聚合的持久化端口（ADR-0030 S1）。由基础设施层实现；由
 * {@link RoleApplicationService} 消费。
 */
public interface RoleRepository {

    /**
     * 持久化 Role，附带数据库身份与领域 {@link Role} 值。
     *
     * @param id          数据库生成的 UUIDv7 主键
     * @param role        领域值（code + name + permissions）
     * @param systemRole  是否为不可删除的内建系统角色
     * @param version     乐观并发版本
     * @param createdAt   行创建时间戳（插入时一次性写入）
     * @param updatedAt   行最后修改时间戳（版本递增时刷新）
     */
    record RoleRecord(UUID id, Role role, boolean systemRole, long version,
                      java.time.Instant createdAt, java.time.Instant updatedAt) {
    }

    UUID save(Role role);

    Optional<RoleRecord> findById(UUID id);

    Optional<RoleRecord> findActiveByCode(String code);

    /**
     * 在当前版本与 {@code expectedVersion} 匹配的前提下，原子替换 {@code roleId} 对应
     * Role 的权限集合。成功返回更新后的版本，版本检查失败返回空。
     */
    Optional<Long> replacePermissions(UUID roleId, Set<PermissionCode> permissions, long expectedVersion);

    Collection<RoleRecord> findAll();

    /**
     * 加载给定 Role ID 授予的权限。供 Binding 解析器重建领域 {@link Role} 实例，
     * 无需按 Binding 逐条往返查询。
     */
    Set<PermissionCode> findPermissionCodesByRoleId(UUID roleId);
}
