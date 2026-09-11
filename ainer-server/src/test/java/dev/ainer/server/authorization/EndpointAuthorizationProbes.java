package dev.ainer.server.authorization;

import dev.ainer.authorization.spring.AinerAuthorize;
import dev.ainer.authorization.spring.EndpointAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点授权运行期行为的探针 Controller 集合（仅测试夹具，不进入交付制品）。
 *
 * <p>五个探针刻意覆盖拦截器的四条分支：未声明（违规形态）、{@code @AinerAuthorize}（决策引擎）、
 * 方法级 {@code @EndpointAccess(PUBLIC)}、方法级 {@code @EndpointAccess(AUTHENTICATED)}、
 * 类级 {@code @EndpointAccess(DELEGATED)}。
 *
 * <p>放在 {@code src/test} 是有意的：静态门禁只扫 {@code src/main/java} 与 Initializer v2 模板，
 * 测试夹具不属于交付端点；未声明探针因此可以长期留在这里，作为「门禁之外的运行期第二层兜底」
 * 的回归证据。
 */
final class EndpointAuthorizationProbes {

    private EndpointAuthorizationProbes() {
    }

    /** 未声明探针：既没有 @AinerAuthorize 也没有 @EndpointAccess。 */
    @RestController
    @RequestMapping("/api/authz-undeclared-probe")
    static class UndeclaredProbeController {

        @GetMapping
        ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }
    }

    /** 注解探针：走 Ainer 决策引擎，行为必须与本次改动前一致。 */
    @RestController
    @RequestMapping("/api/authz-annotated-probe")
    static class AnnotatedProbeController {

        @GetMapping
        @AinerAuthorize(permission = "workspace.read")
        ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }
    }

    /** 显式公开探针：路径同时登记在 public-paths 里，匿名必须可达。 */
    @RestController
    @RequestMapping("/api/authz-public-probe")
    static class PublicProbeController {

        @GetMapping
        @EndpointAccess(
                kind = EndpointAccess.Kind.PUBLIC,
                reason = "测试夹具：验证 PUBLIC 声明 + public-paths 登记后匿名可达")
        ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }
    }

    /** 仅要求登录探针：匿名 401、任何有效 JWT 200（不查权限）。 */
    @RestController
    @RequestMapping("/api/authz-authenticated-probe")
    static class AuthenticatedProbeController {

        @GetMapping
        @EndpointAccess(
                kind = EndpointAccess.Kind.AUTHENTICATED,
                reason = "测试夹具：验证 AUTHENTICATED 声明只放行已认证主体")
        ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }
    }

    /** 类级委托探针：类级声明对类内两个 handler 都生效。 */
    @RestController
    @RequestMapping("/api/authz-delegated-probe")
    @EndpointAccess(
            kind = EndpointAccess.Kind.DELEGATED,
            reason = "测试夹具：验证类级 DELEGATED 声明覆盖类内全部 handler")
    static class DelegatedProbeController {

        @GetMapping
        ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }

        @PostMapping
        ResponseEntity<Void> submit() {
            return ResponseEntity.ok().build();
        }
    }
}
