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
        AinerAdminDevBootstrapProperties equal = properties();
        equal.setMemberUsername(" OWNER@AINER.TEST ");
        RecordingIdentityService equalService = new RecordingIdentityService();

        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(equal, equalService)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
        assertThat(equalService.commands).isEmpty();

        AinerAdminDevBootstrapProperties missing = properties();
        missing.setOwnerPassword("");
        RecordingIdentityService missingService = new RecordingIdentityService();
        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(missing, missingService)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner password");
        assertThat(missingService.commands).isEmpty();
    }

    private AinerAdminDevBootstrapProperties properties() {
        AinerAdminDevBootstrapProperties properties = new AinerAdminDevBootstrapProperties();
        properties.setEnabled(true);
        properties.setOwnerUsername("owner@ainer.test");
        properties.setOwnerPassword("owner-password-2026");
        properties.setOwnerDisplayName("Ainer Admin Owner");
        properties.setMemberUsername("member@ainer.test");
        properties.setMemberPassword("member-password-2026");
        properties.setMemberDisplayName("Ainer Admin Member");
        return properties;
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
