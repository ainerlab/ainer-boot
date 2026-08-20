package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link OAuthClientBinding} 的可变持久化行（MyBatis 普通 POJO 映射，对应表
 * {@code ainer_identity_oauth_client_binding}）。受 ORM 约束使用普通可变类而非 record。
 */
public class OAuthClientBindingRow {

    private UUID id;
    private UUID principalId;
    private String clientId;
    private String status;
    private Instant boundAt;
    private @Nullable Instant unboundAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(UUID principalId) {
        this.principalId = principalId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(Instant boundAt) {
        this.boundAt = boundAt;
    }

    public @Nullable Instant getUnboundAt() {
        return unboundAt;
    }

    public void setUnboundAt(@Nullable Instant unboundAt) {
        this.unboundAt = unboundAt;
    }
}
