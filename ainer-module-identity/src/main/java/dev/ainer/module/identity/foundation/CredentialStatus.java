package dev.ainer.module.identity.foundation;

/**
 * {@link Credential} 的生命周期状态（ADR-0033 Greenfield §4）。
 *
 * <p>只有 {@code ACTIVE} 材料参与认证。{@code REVOKED} 标记被轮换取代或因其他原因
 * 失效的材料；在同一 {@code (account, type)} 插入新的 ACTIVE 材料时，旧材料保留供审计。
 */
public enum CredentialStatus {

    ACTIVE,
    REVOKED
}