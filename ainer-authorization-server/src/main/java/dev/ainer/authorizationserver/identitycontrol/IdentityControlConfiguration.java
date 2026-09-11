package dev.ainer.authorizationserver.identitycontrol;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/**
 * 身份生命周期控制面装配：默认关闭；开启时必须显式登记唯一的可信 SERVICE {@code sub}，
 * 缺失或非法直接启动失败——与 workspace 审计导出的 {@code trusted-exporter-subject} 同一
 * 失败关闭语义，避免"开关开了但白名单是空的"这种静默放行。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.identity-control",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(IdentityControlProperties.class)
public class IdentityControlConfiguration {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    @Bean
    IdentityControlSettings identityControlSettings(IdentityControlProperties properties) {
        String trustedServiceId = properties.getTrustedServiceId();
        if (trustedServiceId == null || !IDENTIFIER.matcher(trustedServiceId.trim()).matches()) {
            throw new IllegalStateException(
                    "Ainer identity-control trusted-service-id is required and must be a valid SERVICE subject");
        }
        return new IdentityControlSettings(trustedServiceId.trim());
    }
}
