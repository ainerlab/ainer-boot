package dev.ainer.module.workspace.workspace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-22T08:00:00Z");
    private static final TenantId TENANT_ID = new TenantId("tenant:test");

    @Test
    void createsAnImmutableWorkspaceAtVersionZero() {
        UUID id = UUID.randomUUID();

        Workspace workspace = Workspace.create(
                id, TENANT_ID, new WorkspaceName(" Ainer研发组 "), CREATED_AT);

        assertThat(workspace.id()).isEqualTo(id);
        assertThat(workspace.tenantId()).isEqualTo(TENANT_ID);
        assertThat(workspace.name().value()).isEqualTo("Ainer研发组");
        assertThat(workspace.version()).isZero();
        assertThat(workspace.createdAt()).isEqualTo(CREATED_AT);
        assertThat(workspace.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void renameAdvancesVersionWithoutMutatingOriginal() {
        Workspace original = Workspace.create(
                UUID.randomUUID(), TENANT_ID, new WorkspaceName("研发空间"), CREATED_AT);
        Instant renamedAt = CREATED_AT.plusSeconds(60);

        Workspace renamed = original.rename(new WorkspaceName("交付空间"), renamedAt);

        assertThat(original.name().value()).isEqualTo("研发空间");
        assertThat(original.version()).isZero();
        assertThat(renamed.name().value()).isEqualTo("交付空间");
        assertThat(renamed.version()).isEqualTo(1);
        assertThat(renamed.updatedAt()).isEqualTo(renamedAt);
    }

    @Test
    void identicalRenameIsIdempotent() {
        Workspace original = Workspace.create(
                UUID.randomUUID(), TENANT_ID, new WorkspaceName("研发空间"), CREATED_AT);

        Workspace unchanged = original.rename(new WorkspaceName(" 研发空间 "), CREATED_AT.plusSeconds(60));

        assertThat(unchanged).isSameAs(original);
    }

    @Test
    void rejectsInvalidNameAndBackwardsTime() {
        assertThatThrownBy(() -> new WorkspaceName(" "))
                .isInstanceOf(IllegalArgumentException.class);

        Workspace original = Workspace.create(
                UUID.randomUUID(), TENANT_ID, new WorkspaceName("研发空间"), CREATED_AT);
        assertThatThrownBy(() -> original.rename(
                new WorkspaceName("交付空间"), CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
