package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.TenantOwnerBootstrapResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 生产首个平台租户与管理员的一次性引导（默认关闭）。见 ADR-0018。镜像既有 bootstrap runner：
 * 启用时按配置创建首个租户及其 OWNER；仅当租户、用户和默认 OWNER 关系完全匹配时幂等跳过。
 * 成功后应立即从运行环境移除开关与明文密码。
 */
@Component
@ConditionalOnProperty(
        prefix = "ainer.platform.tenant-bootstrap",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(PlatformTenantBootstrapProperties.class)
public class PlatformTenantBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformTenantBootstrapRunner.class);

    private final PlatformTenantBootstrapProperties properties;
    private final IdentityApplicationService identityService;

    public PlatformTenantBootstrapRunner(
            PlatformTenantBootstrapProperties properties,
            IdentityApplicationService identityService) {
        this.properties = properties;
        this.identityService = identityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        requireText(properties.getTenantCode(), "tenant code");
        requireText(properties.getTenantName(), "tenant name");
        requireText(properties.getUsername(), "username");
        requireText(properties.getDisplayName(), "display name");
        if (properties.getPassword() == null || properties.getPassword().length() < 12
                || properties.getPassword().length() > 128) {
            throw new IllegalStateException(
                    "Ainer platform tenant bootstrap password must contain 12 to 128 characters");
        }
        TenantOwnerBootstrapResult result = identityService.ensureTenantOwner(new ProvisionTenantOwnerCommand(
                properties.getTenantCode(),
                properties.getTenantName(),
                properties.getUsername(),
                properties.getPassword(),
                properties.getDisplayName()));
        if (result.created()) {
            log.info(
                    "Ainer platform tenant bootstrap created tenant '{}' (owner subject={}) "
                            + "— remove the bootstrap credentials now",
                    properties.getTenantCode(), result.identity().subjectId());
        } else {
            log.info(
                    "Ainer platform tenant bootstrap already complete for tenant '{}' (owner subject={})",
                    properties.getTenantCode(), result.identity().subjectId());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Ainer platform tenant bootstrap " + name + " is required");
        }
    }
}
