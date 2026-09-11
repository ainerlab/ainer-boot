package dev.ainer.authorizationserver.identitycontrol;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ainer.security.authorization-server.identity-control.*} 配置属性。
 *
 * <p>{@code trustedServiceId} 是唯一允许调用身份生命周期控制面的 SERVICE 主体标识，比较对象是
 * 已验证 Token 的 {@code sub}（= ServicePrincipal UUID，见 {@code AinerJwtTokenCustomizer}），
 * 不是可轮换的 OAuth {@code client_id}。留空或非法时启动失败（失败关闭），不做"未配置即放行"。
 */
@ConfigurationProperties("ainer.security.authorization-server.identity-control")
public class IdentityControlProperties {

    private final boolean enabled;
    private final String trustedServiceId;

    public IdentityControlProperties(boolean enabled, String trustedServiceId) {
        this.enabled = enabled;
        this.trustedServiceId = trustedServiceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTrustedServiceId() {
        return trustedServiceId;
    }
}
