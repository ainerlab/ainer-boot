package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;

import java.util.Objects;

/**
 * 启动时校验每个已注册 {@link dev.ainer.authorization.domain Permission} 都在
 * {@link DomainAuthorizationPolicy} 中声明了 {@link GrantPath}（ADR-0030 §5.1、§3.2）。
 * 若任何权限未被覆盖（无声明路径），应用启动失败——这是"对未知或冲突策略 fail closed"
 * 的要求。
 */
public final class PolicyRegistry {

    /**
     * @throws IllegalStateException 当任何已注册权限没有声明 GrantPath 时
     */
    public void validate(PermissionRegistry permissions, DomainAuthorizationPolicy policy) {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(policy, "policy");
        for (var permission : permissions.snapshot()) {
            GrantPath path = policy.pathFor(permission.code());
            if (path == null) {
                throw new IllegalStateException(
                        "No grant path declared for permission '%s'".formatted(permission.code().value()));
            }
        }
    }
}
