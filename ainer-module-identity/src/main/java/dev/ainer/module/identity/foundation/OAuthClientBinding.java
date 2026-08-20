package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一个可轮换的 OAuth {@code client_id} 到稳定 {@link ServicePrincipal} 的受控绑定
 * （ADR-0033 Greenfield §2.6）。
 *
 * <p>一个 ServicePrincipal 可持有 1..n 个 OAuthClientBinding，但同一 {@code client_id}
 * 任一时刻至多一个 {@code ACTIVE} 绑定（基于 {@code (client_id) WHERE status = 'ACTIVE'}
 * 的部分唯一索引）。轮换凭证时旧绑定退役并创建新的 ACTIVE 绑定；退役记录保留供审计，
 * 且历史 token 仍可被 introspection。凭证材料（client secret）不存储在这里——存放在
 * {@code client_id} 引用的 OAuth registered-client 存储中。
 */
public record OAuthClientBinding(
        UUID bindingId,
        UUID principalId,
        String clientId,
        OAuthClientBindingStatus status,
        Instant boundAt,
        @Nullable Instant unboundAt) {

    public OAuthClientBinding {
        Objects.requireNonNull(bindingId, "bindingId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(boundAt, "boundAt");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must be non-blank");
        }
        if (status == OAuthClientBindingStatus.ACTIVE && unboundAt != null) {
            throw new IllegalArgumentException("an ACTIVE binding must not carry an unboundAt timestamp");
        }
    }

    /** 该绑定当前是否允许所关联的 client_id 通过。 */
    public boolean isActive() {
        return status == OAuthClientBindingStatus.ACTIVE;
    }
}
