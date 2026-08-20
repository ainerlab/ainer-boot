package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Workspace 授权审计记录，append-only，记录一次授权检查的完整事实。
 *
 * <p>字段在构造时收紧：所有文本去空白且限长，{@code targetSubjectId} 允许为空（如
 * 工作空间改名这类无目标主体的动作）。审计不保存 Token、prompt 或资源正文，只有稳定
 * 标识与 reason code。
 */
public record WorkspaceAuthorizationAudit(
        UUID id,
        UUID workspaceId,
        String actorSubjectId,
        String targetSubjectId,
        WorkspaceAuthorizationAction action,
        WorkspaceAuthorizationDecision decision,
        String reasonCode,
        Instant occurredAt) {

    public WorkspaceAuthorizationAudit {
        Objects.requireNonNull(id, "id");
        actorSubjectId = requireText(actorSubjectId, "actorSubjectId", 128);
        if (targetSubjectId != null) {
            targetSubjectId = requireText(targetSubjectId, "targetSubjectId", 128);
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(decision, "decision");
        reasonCode = requireText(reasonCode, "reasonCode", 96);
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name, int maxLength) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
