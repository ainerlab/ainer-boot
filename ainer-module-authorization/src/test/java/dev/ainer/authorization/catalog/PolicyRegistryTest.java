package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRegistryTest {

    private static final PermissionCode COVERED = new PermissionCode("doc.read");
    private static final PermissionCode UNCOVERED = new PermissionCode("doc.write");
    private static final ResourceType DOC = new ResourceType("doc");

    @Test
    void validatesWhenAllPermissionsHaveDeclaredPaths() {
        PermissionRegistry permissions = new PermissionRegistry().register(() -> Set.of(
                permission(COVERED),
                permission(UNCOVERED)));
        DomainAuthorizationPolicy policy = bothCovered();
        PolicyRegistry registry = new PolicyRegistry();

        assertThatCode(() -> registry.validate(permissions, policy)).doesNotThrowAnyException();
    }

    @Test
    void failsClosedWhenPermissionLacksDeclaredPath() {
        PermissionRegistry permissions = new PermissionRegistry().register(() -> Set.of(
                permission(COVERED),
                permission(UNCOVERED)));
        DomainAuthorizationPolicy policy = onlyCovered();
        PolicyRegistry registry = new PolicyRegistry();

        assertThatThrownBy(() -> registry.validate(permissions, policy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doc.write");
    }

    private static Permission permission(PermissionCode code) {
        return new Permission(code, "read", DOC, RiskTier.LOW, AuditLevel.ON_DECISION, false, false);
    }

    private static DomainAuthorizationPolicy bothCovered() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return (COVERED.equals(permission) || UNCOVERED.equals(permission))
                        ? GrantPath.BINDING_REQUIRED : null;
            }

            @Override
            public boolean relationGrants(Requester.Authenticated s, PermissionCode p, ResourceRef r, AuthorizationContext c) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(Requester.Authenticated s, PermissionCode p, ResourceRef r, AuthorizationContext c) {
                return true;
            }
        };
    }

    private static DomainAuthorizationPolicy onlyCovered() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return COVERED.equals(permission) ? GrantPath.BINDING_REQUIRED : null;
            }

            @Override
            public boolean relationGrants(Requester.Authenticated s, PermissionCode p, ResourceRef r, AuthorizationContext c) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(Requester.Authenticated s, PermissionCode p, ResourceRef r, AuthorizationContext c) {
                return true;
            }
        };
    }
}
