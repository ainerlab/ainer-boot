package dev.ainer.authorization.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code ainer.security.endpoint-authorization.*} 配置属性（规范见 {@code docs/security.md} §3.4）。
 *
 * <p>{@code mode} 决定未声明端点的处置（默认 {@code fail-closed}）；{@code framework-handler-packages}
 * 登记第三方 jar 提供的 MVC handler 的包前缀。第三方 handler 不是宿主的 Controller，拿不到
 * {@link EndpointAccess}，也无法逐个方法审阅，因此由外层 Resource Server 链负责认证：
 * <ul>
 *   <li>{@code org.springframework.}——Spring Boot 的错误分发（{@code /error}）、Actuator 端点
 *       （含 {@code /actuator/health}、{@code /actuator/prometheus}）；</li>
 *   <li>{@code org.springdoc.}、{@code io.swagger.}——springdoc 的 {@code /v3/api-docs} 与
 *       swagger-ui 资源。生成工程按 ADR-0052 要求这些端点需要有效 JWT，外层链 already 保证这
 *       一点；本配置不会把它们变成匿名端点。</li>
 * </ul>
 *
 * <p>默认值只在「未配置」时生效；宿主可以整体覆盖该列表（覆盖即替换，不支持增量追加）。
 */
@ConfigurationProperties("ainer.security.endpoint-authorization")
public class EndpointAuthorizationProperties {

    /** 未声明端点的默认处置：失败关闭。 */
    public static final EndpointAuthorizationMode DEFAULT_MODE = EndpointAuthorizationMode.FAIL_CLOSED;

    /** 第三方 MVC handler 的默认包前缀（Spring Boot / springdoc）。 */
    public static final List<String> DEFAULT_FRAMEWORK_HANDLER_PACKAGES =
            List.of("org.springframework.", "org.springdoc.", "io.swagger.");

    private final EndpointAuthorizationMode mode;
    private final List<String> frameworkHandlerPackages;

    public EndpointAuthorizationProperties(
            EndpointAuthorizationMode mode,
            List<String> frameworkHandlerPackages) {
        this.mode = mode != null ? mode : DEFAULT_MODE;
        this.frameworkHandlerPackages = frameworkHandlerPackages != null
                ? new ArrayList<>(frameworkHandlerPackages)
                : new ArrayList<>(DEFAULT_FRAMEWORK_HANDLER_PACKAGES);
    }

    public EndpointAuthorizationMode getMode() {
        return mode;
    }

    public List<String> getFrameworkHandlerPackages() {
        return List.copyOf(frameworkHandlerPackages);
    }
}
