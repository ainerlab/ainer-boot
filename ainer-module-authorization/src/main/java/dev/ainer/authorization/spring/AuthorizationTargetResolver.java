package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.ResourceRef;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 从 HTTP 请求解析类型化 {@link ResourceRef} 的产品扩展点（ADR-0037 §4 后续切片）。
 *
 * <p>注册为 Spring bean 后，{@link AinerRequestAuthorizationManager} 会在构建
 * {@link dev.ainer.authorization.domain.AuthorizationRequest} 前按 bean 顺序逐个询问已注册的
 * 解析器；第一个返回非空结果的解析器胜出。所有解析器都返回空（或未注册任何解析器）时，
 * 管理器回退到内置的合成 {@code request} 资源占位，保持既有粗粒度门禁行为不变。
 *
 * <p>契约约束：
 * <ul>
 *   <li>返回的 {@link ResourceRef#resourceType()} 必须与该 permission 在
 *       {@link dev.ainer.authorization.catalog.PermissionRegistry} 中注册的类型一致，
 *       否则决策引擎以 RESOURCE_TYPE_MISMATCH 拒绝（fail-closed）；</li>
 *   <li>解析器抛出的异常不会被视为"无法解析"，而是原样传播并最终映射为服务端错误——
 *       故障的产品组件不得静默降级为合成资源放行；</li>
 *   <li>实现必须只从可信请求状态（路径变量、已验证主体属性等）派生资源标识，
 *       不接受客户端自声明身份。</li>
 * </ul>
 *
 * <p>该接口位于 {@code spring/} 适配器边界（ADR-0037 §3）。
 */
public interface AuthorizationTargetResolver {

    /**
     * 从请求解析授权目标。
     *
     * @param request        当前 HTTP 请求
     * @param permissionCode 已从 handler 注解解析出的稳定权限 code
     * @return 类型化资源引用；{@code empty} 表示该解析器不处理此请求
     */
    Optional<ResourceRef> resolve(HttpServletRequest request, String permissionCode);
}
