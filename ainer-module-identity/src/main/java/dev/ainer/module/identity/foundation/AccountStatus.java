package dev.ainer.module.identity.foundation;

/**
 * {@link HumanAccount} 的安全生命周期状态（ADR-0033 Greenfield §3）。
 *
 * <p>只有 {@code ACTIVE} 可以认证。{@code LOCKED} 是可恢复的限流状态；{@code DISABLED}
 * 是管理端/安全禁用，会使账号级 revocation epoch 失效；{@code CLOSED} 是终态
 * （凭证不可恢复，但下游资源按 ADR 非级联不变量保留）。
 */
public enum AccountStatus {

    ACTIVE,
    LOCKED,
    DISABLED,
    CLOSED;

    /** 只有 ACTIVE 账号可以完成认证。 */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }

    /** 账号仍然存活，可用于恢复/治理（未 CLOSED 且未 DISABLED）。 */
    public boolean isLive() {
        return this == ACTIVE || this == LOCKED;
    }

    /**
     * 状态机是否允许该迁移。失败关闭（fail-closed）：未列出的组合一律拒绝。
     *
     * <ul>
     *   <li>{@code CLOSED} 是终态：离开 CLOSED 的任何迁移都拒绝；</li>
     *   <li>同状态不是变更：重复禁用/重复关闭拒绝，避免空操作伪造审计并再次递增 epoch；</li>
     *   <li>{@code DISABLED -> ACTIVE} 允许：安全禁用经调查/处置后必须能恢复，这是运营必需的
     *       复原方向；{@link #isLive()} 只约束"可用于恢复/治理"，不约束管理员复原；</li>
     *   <li>{@code LOCKED -> ACTIVE} 允许：LOCKED 本身就是可恢复的限流态。</li>
     * </ul>
     */
    public boolean canTransitionTo(AccountStatus target) {
        java.util.Objects.requireNonNull(target, "target");
        return switch (this) {
            case ACTIVE -> target == LOCKED || target == DISABLED || target == CLOSED;
            case LOCKED -> target == ACTIVE || target == DISABLED || target == CLOSED;
            case DISABLED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
