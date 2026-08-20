package dev.ainer.module.file.file.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件模块的只追加变更审计（ADR-0040）。与业务变更同事务写入；被引用的对象行被删除时，
 * {@code fileId} 由数据库置空，因此审计事实的生命周期比文件本身更长。
 */
public record FileAudit(
        UUID id,
        @Nullable UUID fileId,
        String operation,
        String namespace,
        String actorIssuer,
        String actorType,
        String actorId,
        @Nullable String requestId,
        Instant occurredAt) {

    public static final String OPERATION_UPLOADED = "UPLOADED";
    public static final String OPERATION_DELETED = "DELETED";

    public FileAudit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        namespace = requireText(namespace, "namespace");
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
