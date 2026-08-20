package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 单资源授权请求（ADR-0030 §6）。{@link AccessMode} 由端点/用例契约固定，精确选择一条
 * 管道；两条路径互不自动回退。
 */
public record AuthorizationRequest(
        Requester requester,
        AccessMode accessMode,
        PermissionCode permission,
        ResourceRef resource,
        AuthorizationContext context) {

    public AuthorizationRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(accessMode, "accessMode");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(context, "context");
    }
}
