package dev.ainer.security.token;

/**
 * 把当前已认证请求解析为类型化的 Foundation 主体。
 */
public interface AuthenticatedPrincipalResolver {

    AuthenticatedPrincipal requireCurrent();
}
