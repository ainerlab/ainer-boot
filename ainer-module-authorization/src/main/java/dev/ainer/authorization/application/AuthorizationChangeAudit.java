package dev.ainer.authorization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 授权目录管理动作的 append-only 记录（ADR-0030 §11.7、§12.4）。Role/Binding 变更必须与
 * 审计写入同一事务；审计写失败即回滚业务变更。不保存 Token、凭据、prompt 或资源正文。
 *
 * @param id            审计行身份（数据库生成 UUIDv7）
 * @param actorIssuer   操作者 issuer 命名空间，系统发起的变更可为 null
 * @param actorType     {@code USER} 或 {@code SERVICE}，可为 null
 * @param actorId       操作者 subject id，可为 null
 * @param targetType    被变更目标的种类（{@code ROLE}、{@code BINDING}）
 * @param targetId      目标主键
 * @param action        执行的动作（{@code CREATE}、{@code REPLACE_PERMISSIONS}、{@code REVOKE}）
 * @param beforeVersion 变更前目标版本，创建时为 null
 * @param afterVersion  变更后目标版本，可为 null
 * @param requestId     请求追踪 id，可为 null
 * @param traceId       分布式追踪 id，可为 null
 * @param occurredAt    动作发生时间
 */
public record AuthorizationChangeAudit(
        UUID id,
        String actorIssuer,
        String actorType,
        String actorId,
        String targetType,
        UUID targetId,
        String action,
        Long beforeVersion,
        Long afterVersion,
        String requestId,
        String traceId,
        Instant occurredAt) {

    public AuthorizationChangeAudit {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(occurredAt, "occurredAt");
        String normalizedTarget = targetType.trim();
        String normalizedAction = action.trim();
        if (normalizedTarget.isEmpty()) {
            throw new IllegalArgumentException("targetType must not be blank");
        }
        if (normalizedAction.isEmpty()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        targetType = normalizedTarget;
        action = normalizedAction;
    }
}
