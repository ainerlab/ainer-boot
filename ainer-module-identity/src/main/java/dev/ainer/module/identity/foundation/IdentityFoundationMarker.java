package dev.ainer.module.identity.foundation;

/**
 * Greenfield Identity foundation 包的类型安全扫描锚点。被
 * {@code IdentityModuleConfiguration} 的 {@code @ComponentScan} / {@code @MapperScan} 引用，
 * 使 foundation 领域 + 持久化在 S1.2 共存阶段与旧版 {@code account} 包一同装配。
 * 切换移除旧包后，它仍作为 foundation 的扫描锚点保留。
 */
public final class IdentityFoundationMarker {
    private IdentityFoundationMarker() {
    }
}
