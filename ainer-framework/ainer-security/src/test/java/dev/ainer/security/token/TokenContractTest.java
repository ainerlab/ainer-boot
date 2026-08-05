package dev.ainer.security.token;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Greenfield token-profile contracts (S1.1). Covers profile fail-closed resolution, profile↔principal-kind
 * consistency and immutability of resolved principal / claims.
 */
class TokenContractTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://ainer.example/auth");

    @Test
    void tokenProfileResolvesKnownClaimAndFailsClosedOnUnknown() {
        assertThat(TokenProfile.fromClaim("USER_NEUTRAL_V1")).isEqualTo(TokenProfile.USER_NEUTRAL_V1);
        assertThat(TokenProfile.USER_WORKSPACE_V1.claimValue()).isEqualTo("USER_WORKSPACE_V1");
        assertThat(TokenProfile.SERVICE_V1.claimValue()).isEqualTo("SERVICE_V1");
        assertThatThrownBy(() -> TokenProfile.fromClaim("LEGACY_TENANT_V1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenProfile.fromClaim("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TokenProfile.PROFILE_CLAIM).isEqualTo("token_profile");
        assertThat(TokenProfile.CURRENT_CONTRACT_VERSION).isEqualTo("1");
    }

    @Test
    void authenticatedPrincipalAcceptsConsistentProfileAndPrincipalKind() {
        HumanSubjectRef human = new HumanSubjectRef(AUTHORITY, "acc-1");

        AuthenticatedPrincipal neutral = new AuthenticatedPrincipal(
                human, AUTHORITY, TokenProfile.USER_NEUTRAL_V1, "1",
                Set.of("ainer-account"), Set.of("account.read"), "pwd", null);

        assertThat(neutral.isHuman()).isTrue();
        assertThat(neutral.isService()).isFalse();
        assertThat(neutral.principalSubjectRef()).isEqualTo(human);
        assertThat(neutral.tokenProfile()).isEqualTo(TokenProfile.USER_NEUTRAL_V1);
        assertThat(neutral.clientId()).isNull();
    }

    @Test
    void authenticatedPrincipalAcceptsServiceProfileWithServicePrincipal() {
        ServiceSubjectRef service = new ServiceSubjectRef(AUTHORITY, "svc-1");

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                service, AUTHORITY, TokenProfile.SERVICE_V1, "1",
                Set.of("ainer-platform"), Set.of("metrics.read"), "client_credentials", "machine-client-1");

        assertThat(principal.isService()).isTrue();
        assertThat(principal.clientId()).isEqualTo("machine-client-1");
    }

    @Test
    void authenticatedPrincipalRejectsInconsistentProfileAndPrincipalKind() {
        HumanSubjectRef human = new HumanSubjectRef(AUTHORITY, "acc-1");
        ServiceSubjectRef service = new ServiceSubjectRef(AUTHORITY, "svc-1");

        assertThatThrownBy(() -> new AuthenticatedPrincipal(
                human, AUTHORITY, TokenProfile.SERVICE_V1, "1",
                Set.of("a"), Set.of("s"), "x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedPrincipal(
                service, AUTHORITY, TokenProfile.USER_NEUTRAL_V1, "1",
                Set.of("a"), Set.of("s"), "x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedPrincipal(
                service, AUTHORITY, TokenProfile.USER_WORKSPACE_V1, "1",
                Set.of("a"), Set.of("s"), "x", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authenticatedPrincipalCopiesMutableCollections() {
        HumanSubjectRef human = new HumanSubjectRef(AUTHORITY, "acc-1");
        Set<String> audiences = new java.util.HashSet<>(Set.of("a1"));
        Set<String> scopes = new java.util.HashSet<>(Set.of("s1"));

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                human, AUTHORITY, TokenProfile.USER_NEUTRAL_V1, "1", audiences, scopes, "pwd", null);
        audiences.add("a2");
        scopes.add("s2");

        assertThat(principal.audiences()).containsExactly("a1");
        assertThat(principal.scopes()).containsExactly("s1");
    }

    @Test
    void verifiedJwtClaimsAreImmutableAndExposeClaimsReadonly() {
        Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("token_profile", "USER_NEUTRAL_V1");
        VerifiedJwtClaims claims = new VerifiedJwtClaims(
                "https://ainer.example/auth", "acc-1", Set.of("ainer-account"),
                Instant.parse("2026-08-04T12:00:00Z"), raw);

        raw.put("token_profile", "SERVICE_V1");

        assertThat(claims.claim("token_profile")).isEqualTo("USER_NEUTRAL_V1");
        assertThatThrownBy(() -> claims.audiences().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> claims.claims().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
