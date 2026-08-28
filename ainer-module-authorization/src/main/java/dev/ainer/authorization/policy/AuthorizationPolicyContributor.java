package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;

import java.util.Set;

/**
 * 模块级授权策略贡献。宿主产品已经通过 {@link DomainAuthorizationPolicy} 声明的权限始终优先；
 * 只有宿主策略对某权限返回 {@code null} 时，授权模块才会采用一个贡献者的完整策略。
 *
 * <p>贡献者必须同时声明 scope 天花板与领域策略，避免两条配置链由不同模块拼接。多个贡献者
 * 同时认领同一权限属于启动/决策配置冲突，必须失败关闭。权限元数据与策略由同一个贡献者提供，
 * 避免已有宿主只注入单个 {@code PermissionContributor} 时因模块增加同类型 Bean 产生歧义。
 */
public interface AuthorizationPolicyContributor extends DomainAuthorizationPolicy {

    /** 返回该模块实现且参与授权决策的权限元数据。 */
    Set<Permission> permissions();

    /** 返回 OAuth scope 是否可作为该权限的能力上限。 */
    boolean scopePermits(String scope, PermissionCode permission);
}
