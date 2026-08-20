package dev.ainer.module.identity.foundation;

/**
 * {@link LoginIdentity} 绑定的生命周期状态（ADR-0033 Greenfield §4）。
 *
 * <p>{@code ACTIVE} 可用于认证（还需满足其 {@link HumanAccount} 状态与 epoch）。
 * {@code REVOKED} 对该绑定是终态——解绑/重新绑定需要一次全新的验证仪式并产生新绑定；
 * 它绝不能凭自身复活访问权。
 */
public enum LoginIdentityStatus {

    ACTIVE,
    REVOKED
}
