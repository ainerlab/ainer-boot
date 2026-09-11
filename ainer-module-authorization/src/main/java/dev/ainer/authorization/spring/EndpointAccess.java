package dev.ainer.authorization.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点访问口径的显式声明（ADR-0037 §4；规范见 {@code docs/security.md} §3.4、
 * {@code docs/conventions.md} §9）。
 *
 * <p>{@link AinerAuthorize} 是逐方法可选的注解：没有它的 handler 在
 * {@link AinerRequestAuthorizationManager} 里返回 {@code null}，最终落到 Spring Security 的
 * {@code anyRequest().authenticated()}——也就是「只要求登录，不要求任何权限」。这种默认值对
 * 「忘了写注解的新端点」是静默放行，因此本注解提供第三种可能：<strong>显式承认</strong>该端点
 * 不需要 {@code @AinerAuthorize}，并强制写清理由。
 *
 * <p>三种口径（{@link Kind}）互斥，各自对应一种真实存在的端点：
 * <ul>
 *   <li>{@link Kind#PUBLIC}：匿名端点。可达性仍由宿主 Resource Server 的
 *       {@code ainer.security.resource-server.public-paths} 决定——外层 filter chain 先执行，
 *       只有路径同时出现在 public-paths 里，匿名请求才真正可达；两条都登记才算「公开」。
 *       只写本注解不写 public-paths，匿名请求仍是 401（失败关闭方向）。</li>
 *   <li>{@link Kind#AUTHENTICATED}：只要求已认证主体，不要求任何权限（例如平台信息之外的
 *       自助类端点）。这是「登录即可」的显式承认，不是遗漏。</li>
 *   <li>{@link Kind#DELEGATED}：HTTP 层不设 Ainer 权限闸门，细粒度授权由应用服务或该端点
 *       专属的安全构件在内部强制（ADR-0030 §8.4；例如通用授权管理 API 由
 *       {@code GrantAdministrationGuard} 强制，{@code /internal/**} 导出端点由 service scope
 *       强制）。控制面只复核「已认证」，授权结论由被委托方负责。</li>
 * </ul>
 *
 * <p>注解可写在方法上，也可写在类上（类级声明对类内所有 handler 生效）。
 * {@link AinerAuthorize} 只支持方法级（{@code @Target(ElementType.METHOD)}），类级写法编译期
 * 即失败——两者的作用域差异是有意的：权限码必须逐个方法审阅，而「这个 Controller 整体只需要
 * 登录」这类结论天然是类级的。
 *
 * <p>没有本注解也没有 {@link AinerAuthorize} 的 handler，在
 * {@code ainer.security.endpoint-authorization.mode=FAIL_CLOSED}（默认）下被拦截器拒绝并记
 * ERROR 日志；{@code scripts/check-endpoint-authorization.sh} 同时在静态门禁层拦下。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EndpointAccess {

    /** 端点的访问口径。 */
    Kind kind();

    /**
     * 为什么这个端点不需要 {@link AinerAuthorize}。必填，写在源码里供 review 与审计追问：
     * 要能指到具体机制（public-paths 登记、应用服务内的授权调用、专用安全链……）。
     */
    String reason();

    /** 端点访问口径。 */
    enum Kind {

        /** 匿名端点：外层 public-paths 放行后即可未登录访问。 */
        PUBLIC,

        /** 仅要求已认证主体，不要求权限。 */
        AUTHENTICATED,

        /** 授权被委托给应用服务或该端点专属的安全构件在内部强制。 */
        DELEGATED
    }
}
