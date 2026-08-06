package dev.ainer.authorizationserver.identity;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 生产首个 foundation 人员账号的一次性引导（默认关闭）。启用时按配置创建
 * {@code HumanAccount + USERNAME + PASSWORD}；已有 ACTIVE credential 时幂等跳过。
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
    private final AinerAuthorizationServerProperties authorizationProperties;
    private final IdentityFoundationService foundationService;

    public PlatformTenantBootstrapRunner(
            PlatformTenantBootstrapProperties properties,
            AinerAuthorizationServerProperties authorizationProperties,
            IdentityFoundationService foundationService) {
        this.properties = properties;
        this.authorizationProperties = authorizationProperties;
        this.foundationService = foundationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        requireText(properties.getUsername(), "username");
        requireText(properties.getDisplayName(), "display name");
        if (properties.getPassword() == null || properties.getPassword().length() < 12
                || properties.getPassword().length() > 128) {
            throw new IllegalStateException(
                    "Ainer platform tenant bootstrap password must contain 12 to 128 characters");
        }
        String issuer = requireText(authorizationProperties.getIssuer(), "authorization server issuer");
        String username = normalize(properties.getUsername());
        IdentityAuthorityRef authority = new IdentityAuthorityRef(issuer);
        IdentityFoundationService.CredentialLookup existing = foundationService
                .findPasswordCredentialForLogin(LoginIdentityType.USERNAME, issuer, username)
                .orElse(null);
        IdentityFoundationService.RegisteredAccount registered;
        if (existing != null) {
            foundationService.updateProfile(existing.account().accountId(), properties.getDisplayName(), null);
            log.info(
                    "Ainer platform foundation bootstrap already complete (account={})",
                    existing.account().accountId());
            return;
        }
        if (foundationService.findLogin(LoginIdentityType.USERNAME, issuer, username).isPresent()) {
            throw new IllegalStateException(
                    "Ainer platform foundation bootstrap found an incomplete username binding");
        }
        registered = foundationService.registerHumanAccountWithPassword(
                authority, LoginIdentityType.USERNAME, issuer, username, properties.getPassword());
        foundationService.updateProfile(
                registered.account().accountId(), properties.getDisplayName(), null);
        log.info(
                "Ainer platform foundation bootstrap created account '{}' "
                        + "— remove the bootstrap credentials now",
                registered.account().accountId());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Ainer platform tenant bootstrap " + name + " is required");
        }
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
