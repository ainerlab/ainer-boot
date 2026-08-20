package dev.ainer.authorization.policy;

import dev.ainer.authorization.DefaultQueryAuthorizationPlanner;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import org.jspecify.annotations.Nullable;

/**
 * 产品提供的构建器，把一组已授权 {@link SubjectBinding} 翻译为类型化查询约束 {@code Q}
 * （ADR-0030 §7、S3）。
 *
 * <p>Ainer 的 {@link DefaultQueryAuthorizationPlanner} 会对每条授予所请求权限的 live
 * Binding 调用此构建器。产品实现把约束累加起来（例如"允许 workspace X"、"允许资源 Y"、
 * "GLOBAL 则允许全部"）成为单个 {@code Q}，由产品仓储应用到数据库/检索查询。
 *
 * <p>若没有任何 Binding 授予访问，规划器直接返回 {@link AuthorizedQueryPlan.Denied}，
 * 不会构建放行的查询计划。首次调用收到 {@code null} 当前约束；每个实现都必须返回非空
 * 的产品约束。
 *
 * @param <Q> 产品定义的类型化查询约束
 */
@FunctionalInterface
public interface QueryConstraintBuilder<Q> {

    /**
     * 把单条已授权 Binding 累加到正在构建的约束中。
     *
     * <p>典型翻译：
     * <ul>
     *   <li>{@link Scope.Global} → 无约束（可见产品查询返回的全部）</li>
     *   <li>{@link Scope.Workspace} → 限定到该 workspace</li>
     *   <li>{@link Scope.Resource} → 限定到该具体资源</li>
     * </ul>
     *
     * @param current      已累加的约束，首次调用为 {@code null}
     * @param binding      授予所请求权限的 live Binding（绝不可能是 REVOKED）
     * @param permission   被查询的权限（该 Binding 的 role 授予了它）
     * @param resourceType 查询的资源类型（匹配所有相关 Binding）
     * @return 目前累加得到的约束，或首次调用产生的新约束
     */
    Q accumulate(
            @Nullable Q current,
            SubjectBinding binding,
            PermissionCode permission,
            ResourceType resourceType);
}
