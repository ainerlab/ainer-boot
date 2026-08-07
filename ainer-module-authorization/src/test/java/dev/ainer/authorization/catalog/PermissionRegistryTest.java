package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionRegistryTest {

    private static Permission permission(String code) {
        return new Permission(
                new PermissionCode(code), "read", new ResourceType("workspace"),
                RiskTier.LOW, AuditLevel.ON_DECISION, false, false);
    }

    @Test
    void registersAndResolvesContributedPermissions() {
        PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
                permission("platform.metrics.read"),
                permission("workspace.members.read")));

        assertThat(registry.find(new PermissionCode("platform.metrics.read"))).isPresent();
        assertThat(registry.find(new PermissionCode("unknown"))).isEmpty();
        assertThat(registry.snapshot()).hasSize(2);
    }

    @Test
    void duplicateCodeWithIdenticalDefinitionIsIdempotent() {
        Permission permission = permission("workspace.members.read");

        PermissionRegistry registry = new PermissionRegistry()
                .register(permission)
                .register(permission)
                .register(() -> Set.of(permission));

        assertThat(registry.snapshot()).hasSize(1);
    }

    @Test
    void duplicateCodeWithConflictingDefinitionFailsClosed() {
        Permission read = permission("workspace.members.read");
        Permission write = new Permission(
                new PermissionCode("workspace.members.read"), "write", new ResourceType("workspace"),
                RiskTier.HIGH, AuditLevel.ALWAYS, false, false);

        PermissionRegistry registry = new PermissionRegistry().register(read);

        assertThatThrownBy(() -> registry.register(write))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting definitions");
        assertThat(registry.snapshot()).containsOnly(read);
    }
}
