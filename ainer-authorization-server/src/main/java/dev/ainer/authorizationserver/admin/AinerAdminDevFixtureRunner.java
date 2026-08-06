package dev.ainer.authorizationserver.admin;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

class AinerAdminDevFixtureRunner implements ApplicationRunner {

    static final String ADMIN_TENANT_CODE = "ainer-admin-dev";
    static final String ADMIN_TENANT_NAME = "Ainer Admin Development";
    static final String MEMBER_HOME_TENANT_CODE = "ainer-admin-member-home";
    static final String MEMBER_HOME_TENANT_NAME = "Ainer Admin Member Home";

    private static final Logger log = LoggerFactory.getLogger(AinerAdminDevFixtureRunner.class);

    private final AinerAdminDevBootstrapProperties properties;
    private final AinerAuthorizationServerProperties authorizationProperties;
    private final IdentityFoundationService foundationService;

    AinerAdminDevFixtureRunner(
            AinerAdminDevBootstrapProperties properties,
            AinerAuthorizationServerProperties authorizationProperties,
            IdentityFoundationService foundationService) {
        this.properties = properties;
        this.authorizationProperties = authorizationProperties;
        this.foundationService = foundationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        requireText(properties.getOwnerUsername(), "owner username");
        requirePassword(properties.getOwnerPassword(), "owner password");
        requireText(properties.getOwnerDisplayName(), "owner display name");
        requireText(properties.getMemberUsername(), "member username");
        requirePassword(properties.getMemberPassword(), "member password");
        requireText(properties.getMemberDisplayName(), "member display name");
        if (normalize(properties.getOwnerUsername()).equals(normalize(properties.getMemberUsername()))) {
            throw new IllegalStateException(
                    "Ainer Admin dev fixture owner and member usernames must be different");
        }

        String issuer = authorizationProperties.getIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("Ainer Admin dev fixture authorization server issuer is required");
        }
        IdentityAuthorityRef authority = new IdentityAuthorityRef(issuer);
        IdentityFoundationService.RegisteredAccount owner = ensureAccount(
                authority, issuer, properties.getOwnerUsername(), properties.getOwnerPassword(),
                properties.getOwnerDisplayName());
        IdentityFoundationService.RegisteredAccount member = ensureAccount(
                authority, issuer, properties.getMemberUsername(), properties.getMemberPassword(),
                properties.getMemberDisplayName());
        log.info(
                "Ainer Admin dev foundation fixture ready (owner account={}, member account={})",
                owner.account().accountId(), member.account().accountId());
    }

    private IdentityFoundationService.RegisteredAccount ensureAccount(
            IdentityAuthorityRef authority,
            String issuer,
            String rawUsername,
            String password,
            String displayName) {
        String username = normalize(rawUsername);
        IdentityFoundationService.CredentialLookup existing = foundationService
                .findPasswordCredentialForLogin(LoginIdentityType.USERNAME, issuer, username)
                .orElse(null);
        if (existing != null) {
            foundationService.updateProfile(existing.account().accountId(), displayName, null);
            return new IdentityFoundationService.RegisteredAccount(
                    existing.account(),
                    foundationService.findLogin(LoginIdentityType.USERNAME, issuer, username).orElseThrow());
        }
        if (foundationService.findLogin(LoginIdentityType.USERNAME, issuer, username).isPresent()) {
            throw new IllegalStateException(
                    "Ainer Admin dev fixture found an incomplete username binding");
        }
        IdentityFoundationService.RegisteredAccount registered = foundationService
                .registerHumanAccountWithPassword(
                        authority, LoginIdentityType.USERNAME, issuer, username, password);
        foundationService.updateProfile(registered.account().accountId(), displayName, null);
        return registered;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Ainer Admin dev fixture " + name + " is required");
        }
    }

    private static void requirePassword(String value, String name) {
        if (value == null || value.length() < 12 || value.length() > 128) {
            throw new IllegalStateException(
                    "Ainer Admin dev fixture " + name + " must contain 12 to 128 characters");
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
