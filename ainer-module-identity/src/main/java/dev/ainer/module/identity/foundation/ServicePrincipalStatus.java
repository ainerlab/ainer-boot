package dev.ainer.module.identity.foundation;

/**
 * {@link ServicePrincipal} 的生命周期状态（ADR-0033 Greenfield §2.6）。
 *
 * <p>C1 foundation 基线刻意保持服务生命周期最小化：service principal 要么 {@code ACTIVE}
 * （可认证、可绑定凭证），要么 {@code DISABLED}（已吊销；早于当前 epoch 的凭证与 token
 * 全部失效）。更细粒度的锁定/关闭状态推迟到出现具体运营需求时再引入。
 */
public enum ServicePrincipalStatus {

    ACTIVE,
    DISABLED;

    /** 该 principal 是否可以认证或持有活跃凭证绑定。 */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
