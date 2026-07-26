package dev.ainer.authorizationserver.admin;

import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.TenantOwnerBootstrapResult;
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
    private final IdentityApplicationService identityService;

    AinerAdminDevFixtureRunner(
            AinerAdminDevBootstrapProperties properties,
            IdentityApplicationService identityService) {
        this.properties = properties;
        this.identityService = identityService;
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

        TenantOwnerBootstrapResult owner = identityService.ensureTenantOwner(
                new ProvisionTenantOwnerCommand(
                        ADMIN_TENANT_CODE,
                        ADMIN_TENANT_NAME,
                        properties.getOwnerUsername(),
                        properties.getOwnerPassword(),
                        properties.getOwnerDisplayName()));
        TenantOwnerBootstrapResult member = identityService.ensureTenantOwner(
                new ProvisionTenantOwnerCommand(
                        MEMBER_HOME_TENANT_CODE,
                        MEMBER_HOME_TENANT_NAME,
                        properties.getMemberUsername(),
                        properties.getMemberPassword(),
                        properties.getMemberDisplayName()));
        log.info(
                "Ainer Admin dev fixture ready (admin tenant={}, owner subject={}, member home tenant={}, "
                        + "member subject={})",
                owner.identity().tenantId(),
                owner.identity().subjectId(),
                member.identity().tenantId(),
                member.identity().subjectId());
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
