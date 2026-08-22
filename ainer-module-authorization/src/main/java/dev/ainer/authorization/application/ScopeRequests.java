package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.core.error.BusinessException;

import java.util.Set;
import java.util.UUID;

/**
 * HTTP 请求字段到 {@link Scope} 的解析规则。保留 resourceType 的白名单属于授权安全
 * 不变量：内部子集校验的合成锚点类型不得经公开 API 声明，否则可伪造绑定绕过
 * SubjectSet/ActingGrant 的成员校验——因此该规则放在应用层而不是 Controller。
 */
public final class ScopeRequests {

    /** 保留 resourceType：内部子集校验合成锚点使用，不得经 API 声明。 */
    private static final Set<String> RESERVED_RESOURCE_TYPES = Set.of(
            "workspace.anchor", "request");

    private ScopeRequests() {
    }

    public static Scope buildScope(
            String scopeKind, UUID workspaceId, String resourceType, UUID resourceId) {
        return switch (scopeKind) {
            case "GLOBAL" -> new Scope.Global();
            case "WORKSPACE" -> {
                if (workspaceId == null) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Workspace(workspaceId);
            }
            case "RESOURCE" -> {
                if (workspaceId == null || resourceType == null || resourceId == null
                        || RESERVED_RESOURCE_TYPES.contains(resourceType)) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Resource(workspaceId, new ResourceType(resourceType), resourceId);
            }
            default -> throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
        };
    }
}
