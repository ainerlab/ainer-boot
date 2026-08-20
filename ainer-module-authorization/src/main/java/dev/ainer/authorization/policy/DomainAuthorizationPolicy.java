package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;

/**
 * 为动作声明已认证授权路径，并求值两个相互独立的侧面（ADR-0030 §5.1、§6.1）：
 *
 * <ul>
 *   <li>{@link #relationGrants} —— 关系派生授权（所有者/参与者关系即权威）。
 *   <li>{@link #resourceStateSatisfies} —— 领域策略的资源状态/关系条件，与所有授权路径求交集。
 * </ul>
 *
 * 求值器按声明的 {@link GrantPath} 计算：{@code (bindingGrant ∪ relationGrant) ∩
 * resourceStateSatisfies}。两个侧面单独都不充分；对选定路径必须同时成立。
 */
public interface DomainAuthorizationPolicy {

    @Nullable GrantPath pathFor(PermissionCode permission);

    boolean relationGrants(
            Requester.Authenticated subject,
            PermissionCode permission,
            ResourceRef resource,
            AuthorizationContext context);

    boolean resourceStateSatisfies(
            Requester.Authenticated subject,
            PermissionCode permission,
            ResourceRef resource,
            AuthorizationContext context);
}
