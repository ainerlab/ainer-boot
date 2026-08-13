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

/** One-shot, explicitly enabled bootstrap for a foundation HumanAccount. */
@Component
@ConditionalOnProperty(
        prefix = "ainer.platform.account-bootstrap",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(PlatformAccountBootstrapProperties.class)
public class PlatformAccountBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAccountBootstrapRunner.class);

    private final PlatformAccountBootstrapProperties properties;
    private final AinerAuthorizationServerProperties authorizationProperties;
    private final IdentityFoundationService foundationService;

    public PlatformAccountBootstrapRunner(
            PlatformAccountBootstrapProperties properties,
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
        if (properties.getPassword() == null
                || properties.getPassword().length() < 12
                || properties.getPassword().length() > 128) {
            throw new IllegalStateException("Ainer account bootstrap password must contain 12 to 128 characters");
        }

        String issuer = requireText(authorizationProperties.getIssuer(), "authorization server issuer");
        String username = normalize(properties.getUsername());
        IdentityFoundationService.CredentialLookup existing = foundationService
                .findPasswordCredentialForLogin(LoginIdentityType.USERNAME, issuer, username)
                .orElse(null);
        if (existing != null) {
            foundationService.updateProfile(existing.account().accountId(), properties.getDisplayName(), null);
            log.info("Ainer account bootstrap already complete (account={})", existing.account().accountId());
            return;
        }
        if (foundationService.findLogin(LoginIdentityType.USERNAME, issuer, username).isPresent()) {
            throw new IllegalStateException("Ainer account bootstrap found an incomplete username binding");
        }
        IdentityFoundationService.RegisteredAccount registered = foundationService
                .registerHumanAccountWithPassword(
                        new IdentityAuthorityRef(issuer), LoginIdentityType.USERNAME,
                        issuer, username, properties.getPassword());
        foundationService.updateProfile(registered.account().accountId(), properties.getDisplayName(), null);
        log.info("Ainer account bootstrap created account; remove the bootstrap credentials now");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Ainer account bootstrap " + name + " is required");
        }
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
