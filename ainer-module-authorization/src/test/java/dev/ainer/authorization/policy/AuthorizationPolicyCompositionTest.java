package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationPolicyCompositionTest {

    private static final PermissionCode HOST_PERMISSION = new PermissionCode("host.read");
    private static final PermissionCode MODULE_PERMISSION = new PermissionCode("workspace.read");

    @Test
    void moduleFillsPermissionNotClaimedByHost() {
        DomainAuthorizationPolicy composed = AuthorizationPolicyComposition.domainPolicy(
                hostPolicy(), List.of(contributor(MODULE_PERMISSION, GrantPath.RELATION_DERIVED)));
        ScopePermissionCeiling ceiling = AuthorizationPolicyComposition.scopeCeiling(
                (scope, permission) -> scope.equals("host.scope"),
                hostPolicy(),
                List.of(contributor(MODULE_PERMISSION, GrantPath.RELATION_DERIVED)));

        assertThat(composed.pathFor(HOST_PERMISSION)).isEqualTo(GrantPath.BINDING_REQUIRED);
        assertThat(composed.pathFor(MODULE_PERMISSION)).isEqualTo(GrantPath.RELATION_DERIVED);
        assertThat(ceiling.permits("workspace.read", MODULE_PERMISSION)).isTrue();
    }

    @Test
    void hostPolicyOwnsAllSemanticsForClaimedPermission() {
        AuthorizationPolicyContributor conflictingContributor =
                contributor(HOST_PERMISSION, GrantPath.RELATION_DERIVED);
        DomainAuthorizationPolicy composed = AuthorizationPolicyComposition.domainPolicy(
                hostPolicy(), List.of(conflictingContributor));
        ScopePermissionCeiling ceiling = AuthorizationPolicyComposition.scopeCeiling(
                (scope, permission) -> false,
                hostPolicy(),
                List.of(conflictingContributor));

        assertThat(composed.pathFor(HOST_PERMISSION)).isEqualTo(GrantPath.BINDING_REQUIRED);
        assertThat(ceiling.permits("host.read", HOST_PERMISSION)).isFalse();
    }

    @Test
    void multipleContributorsForSamePermissionFailClosed() {
        DomainAuthorizationPolicy composed = AuthorizationPolicyComposition.domainPolicy(
                hostPolicy(),
                List.of(
                        contributor(MODULE_PERMISSION, GrantPath.RELATION_DERIVED),
                        contributor(MODULE_PERMISSION, GrantPath.BINDING_REQUIRED)));

        assertThatThrownBy(() -> composed.pathFor(MODULE_PERMISSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspace.read");
    }

    private static DomainAuthorizationPolicy hostPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return HOST_PERMISSION.equals(permission) ? GrantPath.BINDING_REQUIRED : null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return HOST_PERMISSION.equals(permission);
            }
        };
    }

    private static AuthorizationPolicyContributor contributor(
            PermissionCode claimed, GrantPath path) {
        return new AuthorizationPolicyContributor() {
            @Override
            public Set<Permission> permissions() {
                return Set.of();
            }

            @Override
            public boolean scopePermits(String scope, PermissionCode permission) {
                return claimed.equals(permission) && scope.equals(permission.value());
            }

            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return claimed.equals(permission) ? path : null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return claimed.equals(permission);
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return claimed.equals(permission);
            }
        };
    }
}
