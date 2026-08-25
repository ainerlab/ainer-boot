package dev.ainer.server.authorization;

import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.spring.AuthorizationTargetResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参考装配的 Workspace 路径目标解析器：把 {@code /api/workspaces/{id}} 上的路径变量写成
 * {@link ResourceRef#workspaceId()}，使 WORKSPACE Binding 必须对上该工作区。
 *
 * <p>permission 仍按平台目录注册为 {@code resourceType=request}，本解析器不改资源类型，
 * 避免 RESOURCE_TYPE_MISMATCH。无路径工作区（创建/分页列表）返回 empty，回退到无
 * workspaceId 的合成 request——任一 WORKSPACE Binding 仍可作为「持有该权限」粗闸门。
 *
 * <p>只读 MVC 路径变量与 servlet path，不接受 query / header / 请求体里的工作区声明。
 * 这不是 1.x 资源级授权合同：所有权与成员关系仍由 Workspace 应用服务检查。
 */
public final class WorkspacePathAuthorizationTargetResolver implements AuthorizationTargetResolver {

    static final Set<String> WORKSPACE_PERMISSIONS = Set.of(
            "workspace.read", "workspace.write", "workspace.audit.read");

    private static final Pattern WORKSPACE_PATH = Pattern.compile(
            "^/api/workspaces/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");

    @Override
    public Optional<ResourceRef> resolve(HttpServletRequest request, String permissionCode) {
        if (permissionCode == null || !WORKSPACE_PERMISSIONS.contains(permissionCode)) {
            return Optional.empty();
        }
        String servletPath = request.getServletPath();
        if (servletPath == null || !servletPath.startsWith("/api/workspaces/")) {
            return Optional.empty();
        }
        Optional<String> raw = pathWorkspaceId(request, servletPath);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        UUID workspaceId = parseWorkspaceId(raw.get());
        return Optional.of(new ResourceRef(
                workspaceId, AinerServerAuthorizationPolicyConfiguration.REQUEST_RESOURCE, workspaceId));
    }

    private static Optional<String> pathWorkspaceId(HttpServletRequest request, String servletPath) {
        Object rawVars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (rawVars instanceof Map<?, ?> vars) {
            Object named = firstPresent(vars.get("id"), vars.get("workspaceId"));
            if (named instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        Matcher matcher = WORKSPACE_PATH.matcher(servletPath);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static Object firstPresent(Object first, Object second) {
        return first != null ? first : second;
    }

    private static UUID parseWorkspaceId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("workspace path variable is not a UUID", ex);
        }
    }
}
