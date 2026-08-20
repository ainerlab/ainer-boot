package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 公开字段投影描述符（ADR-0030 §5.2、§6.5）。当匿名/公开访问被允许时由
 * {@code PublicAccessPolicy} 返回；HTTP 适配器必须在发送响应前应用该投影。
 * 没有投影的裸布尔 ALLOW 是不充分的。
 */
public record PublicProjection(String descriptor) implements DecisionObligation {

    public PublicProjection {
        Objects.requireNonNull(descriptor, "descriptor");
        String normalized = descriptor.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("projection descriptor must not be blank");
        }
        descriptor = normalized;
    }
}
