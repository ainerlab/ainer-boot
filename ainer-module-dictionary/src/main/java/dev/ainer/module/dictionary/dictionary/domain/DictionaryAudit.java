package dev.ainer.module.dictionary.dictionary.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 字典模块的只追加变更审计（ADR-0040）。与业务变更同事务写入；
 * 审计失败会连同业务变更一起回滚。
 */
public record DictionaryAudit(
        UUID id,
        String operation,
        String targetKind,
        UUID targetId,
        String actorIssuer,
        String actorType,
        String actorId,
        @Nullable String requestId,
        @Nullable String detail,
        Instant occurredAt) {

    public static final String TARGET_TYPE = "TYPE";
    public static final String TARGET_ITEM = "ITEM";

    public static final String OPERATION_TYPE_CREATED = "TYPE_CREATED";
    public static final String OPERATION_TYPE_UPDATED = "TYPE_UPDATED";
    public static final String OPERATION_TYPE_STATUS_CHANGED = "TYPE_STATUS_CHANGED";
    public static final String OPERATION_ITEM_CREATED = "ITEM_CREATED";
    public static final String OPERATION_ITEM_UPDATED = "ITEM_UPDATED";
    public static final String OPERATION_ITEM_STATUS_CHANGED = "ITEM_STATUS_CHANGED";

    public DictionaryAudit {
        Objects.requireNonNull(id, "id");
        operation = requireText(operation, "operation");
        targetKind = requireText(targetKind, "targetKind");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actorIssuer, "actorIssuer");
        Objects.requireNonNull(actorId, "actorId");
        if (!"USER".equals(actorType) && !"SERVICE".equals(actorType)) {
            throw new IllegalArgumentException("actorType must be USER or SERVICE");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }
}
