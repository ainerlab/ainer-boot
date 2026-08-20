package dev.ainer.module.identity.foundation;

/**
 * 把可轮换 OAuth {@code client_id} 连接到稳定 {@link ServicePrincipal} 的
 * {@link OAuthClientBinding} 状态（ADR-0033 Greenfield §2.6）。
 *
 * <p>同一 {@code client_id} 任一时刻至多一个 {@code ACTIVE} 绑定（由部分唯一索引强制）；
 * {@code RETIRED} 绑定在新凭证占用同一 {@code client_id} 后保留，供审计与历史 token
 * introspection。
 */
public enum OAuthClientBindingStatus {

    ACTIVE,
    RETIRED;

    /** 该绑定当前是否授权所关联的 client_id 以其 principal 身份认证。 */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
