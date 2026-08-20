package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 授权请求面向的具体资源引用（ADR-0030 §4.6、§6.2）。{@code workspaceId} 是可选的
 * 访问上下文事实。产品归属/从属关系的权威仍在其所属模块中，不会从该引用反推重建。
 */
public record ResourceRef(
        @Nullable UUID workspaceId,
        ResourceType resourceType,
        UUID resourceId) {

    public ResourceRef {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
    }
}
