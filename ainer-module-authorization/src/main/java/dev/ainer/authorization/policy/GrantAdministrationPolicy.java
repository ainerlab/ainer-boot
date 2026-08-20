package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;

/**
 * 代码注册、带版本的授权管理策略（ADR-0030 §11）。
 *
 * <p>使用一个权限与分配一个权限是被刻意分开的能力。实现定义精确的受信管理主体，
 * 以及它们可分配的权限、scope 与目标主体。这些决策必须来自产品/平台代码——既不来自
 * actor 的有效权限，也不来自任意请求数据。
 *
 * <p>通用模块安装全拒绝实现。宿主应用必须显式贡献一个策略，任何授权管理端点或变更
 * 才可用。
 */
public interface GrantAdministrationPolicy {

    /** 宿主记录的稳定版本，用于策略发布与审计关联。 */
    String version();

    /** 这个精确的已验证主体是否是受信的授权管理者。 */
    boolean isTrustedManager(AuthenticatedPrincipal actor);

    /** 管理者是否可把该已注册权限放进受管 Role。 */
    boolean isPermissionAssignable(AuthenticatedPrincipal actor, Permission permission);

    /** 管理者是否可创建携带该结构化 scope 的 Binding。 */
    boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope);

    /** 管理者是否可为该 authority 限定目标创建 Binding。 */
    boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target);
}
