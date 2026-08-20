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
}
