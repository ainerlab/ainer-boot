package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AccessMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 handler 方法所需的 Ainer 权限（ADR-0037 §4、ADR-0030 §8.3）。
 *
 * <p>注解引用的是注册在 {@link dev.ainer.authorization.catalog.PermissionRegistry} 中的
 * <strong>稳定 PermissionCode</strong>。它不包含 SpEL 或任意策略——只是一个声明式标记，
 * 由 {@link AinerAuthorizeInterceptor} 消费：在 MVC handler 解析之后、进入控制器之前调用
 * {@link AinerRequestAuthorizationManager}。
 *
 * <p>{@link #accessMode()} 默认为 {@link AccessMode#AUTHENTICATED 已认证}。只有当方法
 * 同时通过显式 {@link dev.ainer.authorization.policy.PublicAccessPolicy} 服务匿名访问、
 * 且宿主安全配置放行该路径时，才能选择 {@link AccessMode#PUBLIC_PROJECTION 公开}模式。
 * 0.1 版适配器尚不执行产生的投影义务，因此在安装义务执行器之前公开投影保持
 * fail-closed。
 *
 * <p>高风险业务写操作仍必须在应用服务中显式调用
 * {@link dev.ainer.authorization.AuthorizationService}（ADR-0030 §8.4）——本注解是 HTTP 层
 * 的粗粒度闸门，不能替代应用层的细粒度授权。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AinerAuthorize {

    /** 访问该方法所需的稳定权限 code。 */
    String permission();

    /** 端点的访问模式。默认 {@link AccessMode#AUTHENTICATED}。 */
    AccessMode accessMode() default AccessMode.AUTHENTICATED;
}
