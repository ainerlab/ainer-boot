package dev.ainer.authorizationserver.admin;

import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.account.application.TenantOwnerBootstrapResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerAdminDevFixtureRunnerTest {

    @Test
    void provisionsOwnerAndLoginCapableSecondUserInSeparateTenants() {
        AinerAdminDevBootstrapProperties properties = properties();
        RecordingIdentityService identityService = new RecordingIdentityService();

        new AinerAdminDevFixtureRunner(properties, identityService)
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(identityService.commands)
                .extracting(ProvisionTenantOwnerCommand::tenantCode)
                .containsExactly("ainer-admin-dev", "ainer-admin-member-home");
        assertThat(identityService.commands)
                .extracting(ProvisionTenantOwnerCommand::username)
                .containsExactly("owner@ainer.test", "member@ainer.test");
        assertThat(identityService.commands)
                .extracting(ProvisionTenantOwnerCommand::rawPassword)
                .containsExactly("owner-password-2026", "member-password-2026");
    }

    @Test
    void equalUsernamesAndMissingSecretsFailBeforeWriting() {
        AinerAdminDevBootstrapProperties equal =
                properties(" OWNER@AINER.TEST ", "owner-password-2026");
        RecordingIdentityService equalService = new RecordingIdentityService();

        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(equal, equalService)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
        assertThat(equalService.commands).isEmpty();

        AinerAdminDevBootstrapProperties missing = properties("member@ainer.test", "");
        RecordingIdentityService missingService = new RecordingIdentityService();
        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(missing, missingService)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner password");
        assertThat(missingService.commands).isEmpty();
    }

    private AinerAdminDevBootstrapProperties properties() {
        return properties("member@ainer.test", "owner-password-2026");
    }

    private AinerAdminDevBootstrapProperties properties(String memberUsername, String ownerPassword) {
        return new AinerAdminDevBootstrapProperties(
                true,
                "owner@ainer.test",
                ownerPassword,
                "Ainer Admin Owner",
                memberUsername,
                "member-password-2026",
                "Ainer Admin Member");
    }

    private static final class RecordingIdentityService extends IdentityApplicationService {

        private final List<ProvisionTenantOwnerCommand> commands = new ArrayList<>();

        private RecordingIdentityService() {
            super(null, null, null);
        }

        @Override
        public TenantOwnerBootstrapResult ensureTenantOwner(ProvisionTenantOwnerCommand command) {
            commands.add(command);
            ProvisionedIdentity identity = new ProvisionedIdentity(
                    UUID.randomUUID(), UUID.randomUUID(), command.username());
            return new TenantOwnerBootstrapResult(identity, true);
        }
    }
}
