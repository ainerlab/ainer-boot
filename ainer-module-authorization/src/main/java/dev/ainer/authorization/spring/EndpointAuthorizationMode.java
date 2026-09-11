package dev.ainer.authorization.spring;

/**
 * 未声明端点（既没有 {@link AinerAuthorize} 也没有 {@link EndpointAccess} 的 handler）的处置模式
 * （{@code ainer.security.endpoint-authorization.mode}）。
 *
 * <p>默认 {@link #FAIL_CLOSED}：默认拒绝必须同时成立于决策引擎内部和端点层。只写在引擎内部
 * （未知权限/无策略/无绑定 → DENY）意味着「忘了写注解」的新端点是静默放行，这正是本配置要
 * 消除的缺口。宿主如需在升级期间保留旧行为，必须显式配置 {@code mode: warn}——把默认值改成
 * 「宽容」，再由每个宿主自己想起来加固，等于把安全默认值交给运气。
 */
public enum EndpointAuthorizationMode {

    /** 未声明端点直接 403，并记 ERROR 日志（默认）。 */
    FAIL_CLOSED,

    /** 未声明端点放行（沿用旧行为，仅要求已认证），但记 WARN 日志，供升级期灰度。 */
    WARN
}
