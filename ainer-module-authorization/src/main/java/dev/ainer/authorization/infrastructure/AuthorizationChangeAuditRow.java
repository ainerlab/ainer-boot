package dev.ainer.authorization.infrastructure;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code ainer_authorization_change_audit} 的行映射。{@code id} 由数据库生成
 * （UUIDv7 DEFAULT），插入时不设置。
 */
public class AuthorizationChangeAuditRow {

    private @Nullable UUID id;
    private @Nullable String actorIssuer;
    private @Nullable String actorType;
    private @Nullable String actorId;
    private String targetType;
    private UUID targetId;
    private String action;
    private @Nullable Long beforeVersion;
    private @Nullable Long afterVersion;
    private @Nullable String requestId;
    private @Nullable String traceId;
    private Instant occurredAt;

    public @Nullable UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public @Nullable String getActorIssuer() { return actorIssuer; }
    public void setActorIssuer(String actorIssuer) { this.actorIssuer = actorIssuer; }

    public @Nullable String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public @Nullable String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public @Nullable Long getBeforeVersion() { return beforeVersion; }
    public void setBeforeVersion(Long beforeVersion) { this.beforeVersion = beforeVersion; }

    public @Nullable Long getAfterVersion() { return afterVersion; }
    public void setAfterVersion(Long afterVersion) { this.afterVersion = afterVersion; }

    public @Nullable String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public @Nullable String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
