package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 绑定到 {@link SubjectBinding} 的授权 scope（ADR-0030 §4.2）。第一版只支持这三种 scope
 * 种类；嵌套/递归 scope 与任意 JSON 条件被显式排除在外。
 *
 * <p>Workspace 与 Resource scope 是相互独立的类型化上限。资源归属由产品解析器提供，
 * 不从某个全局父概念推导。
 */
public sealed interface Scope permits Scope.Global, Scope.Workspace, Scope.Resource {

    /**
     * 该 scope 是否权威地覆盖给定资源（ADR-0030 §6.2）。第一版不做递归父级遍历、
     * 路径通配或 scope 树。
     */
    boolean covers(ResourceRef resource);

    /** 平台全局 scope；仅受控的平台服务可持有。 */
    record Global() implements Scope {
        @Override
        public boolean covers(ResourceRef resource) {
            return true;
        }
    }

    /** 恰好限定到一个 Workspace。 */
    record Workspace(UUID workspaceId) implements Scope {
        public Workspace {
            Objects.requireNonNull(workspaceId, "workspaceId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            if (resource.workspaceId() != null) {
                return resource.workspaceId().equals(workspaceId);
            }
            // HTTP 粗门禁：拦截器合成的 request 资源常无 workspaceId。
            // 任一 WORKSPACE Binding 即可满足「持有该权限」闸门，不是资源级合同。
            return "request".equals(resource.resourceType().value());
        }
    }

    /** 限定到一个具体资源，锚定到拥有该资源的 Workspace。 */
    record Resource(UUID workspaceId, ResourceType resourceType, UUID resourceId) implements Scope {
        public Resource {
            Objects.requireNonNull(workspaceId, "workspaceId");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(resourceId, "resourceId");
        }

        @Override
        public boolean covers(ResourceRef resource) {
            return resourceType.equals(resource.resourceType())
                    && resourceId.equals(resource.resourceId());
        }
    }
}
