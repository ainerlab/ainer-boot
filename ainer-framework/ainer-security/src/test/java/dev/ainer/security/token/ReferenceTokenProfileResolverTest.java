package dev.ainer.security.token;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the {@link ReferenceTokenProfileResolver} fail-closed contract: closed profiles resolve to the
 * matching principal kind, while unknown profile / version / actor-type mismatch all reject (ADR-0033 §6.1).
 */
class ReferenceTokenProfileResolverTest {

    private final ReferenceTokenProfileResolver resolver = new ReferenceTokenProfileResolver();

    private static VerifiedJwtClaims claims(String profile, String actorType, String subject,
                                            Map<String, Object> extra) {
        Map<String, Object> base = new HashMap<>();
        base.put(TokenProfile.PROFILE_CLAIM, profile);
        base.put(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION);
        base.put("actor_type", actorType);
        base.put("scope", "account.read");
        base.put("amr", "pwd");
        base.putAll(extra);
        return new VerifiedJwtClaims(
                "https://ainer.example/auth", subject, Set.of("ainer-account"),
                Instant.parse("2026-08-05T12:00:00Z"), base);
    }

    @Test
    void resolvesUserNeutralToHumanPrincipal() {
        AuthenticatedPrincipal principal = resolver.resolve(
                claims("USER_NEUTRAL_V1", "USER", "acc-1", Map.of()));

        assertThat(principal.isHuman()).isTrue();
        assertThat(principal.tokenProfile()).isEqualTo(TokenProfile.USER_NEUTRAL_V1);
        assertThat(principal.principalSubjectRef().subjectId()).isEqualTo("acc-1");
        assertThat(principal.scopes()).containsExactly("account.read");
        assertThat(principal.clientId()).isNull();
    }

    @Test
    void resolvesUserWorkspaceToHumanPrincipal() {
        AuthenticatedPrincipal principal = resolver.resolve(
                claims("USER_WORKSPACE_V1", "USER", "acc-2", Map.of()));

        assertThat(principal.isHuman()).isTrue();
        assertThat(principal.tokenProfile()).isEqualTo(TokenProfile.USER_WORKSPACE_V1);
    }

    @Test
    void resolvesServiceToServicePrincipalWithClientId() {
        AuthenticatedPrincipal principal = resolver.resolve(
                claims("SERVICE_V1", "SERVICE", "svc-1", Map.of("client_id", "machine-1")));

        assertThat(principal.isService()).isTrue();
        assertThat(principal.clientId()).isEqualTo("machine-1");
    }

    @Test
    void resolvesOptionalSecurityEpochAndUsesServiceAssuranceWithoutAmr() {
        AuthenticatedPrincipal human = resolver.resolve(
                claims("USER_NEUTRAL_V1", "USER", "acc-epoch", Map.of("sec_epoch", 7L)));
        assertThat(human.securityEpoch()).isEqualTo(7L);

        Map<String, Object> serviceClaims = new HashMap<>();
        serviceClaims.put(TokenProfile.PROFILE_CLAIM, "SERVICE_V1");
        serviceClaims.put(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION);
        serviceClaims.put("actor_type", "SERVICE");
        serviceClaims.put("scope", "account.read");
        serviceClaims.put("sec_epoch", 3L);
        AuthenticatedPrincipal service = resolver.resolve(new VerifiedJwtClaims(
                "https://ainer.example/auth", "svc-epoch", Set.of("ainer-api"),
                Instant.parse("2026-08-05T12:00:00Z"), serviceClaims));
        assertThat(service.securityEpoch()).isEqualTo(3L);
        assertThat(service.assurance()).isEqualTo("client_credentials");
    }

    @Test
    void rejectsUnknownProfile() {
        assertThatThrownBy(() -> resolver.resolve(
                claims("LEGACY_TENANT_V1", "USER", "acc-1", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        assertThatThrownBy(() -> resolver.resolve(
                claims("USER_NEUTRAL_V1", "USER", "acc-1", Map.of("claim_contract_version", "0"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsActorTypeInconsistentWithProfile() {
        assertThatThrownBy(() -> resolver.resolve(
                claims("SERVICE_V1", "USER", "acc-1", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(
                claims("USER_NEUTRAL_V1", "SERVICE", "svc-1", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingProfileClaim() {
        Map<String, Object> base = new HashMap<>();
        base.put(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION);
        base.put("actor_type", "USER");
        base.put("scope", "account.read");
        base.put("amr", "pwd");
        VerifiedJwtClaims withoutProfile = new VerifiedJwtClaims(
                "https://ainer.example/auth", "acc-1", Set.of("ainer-account"),
                Instant.parse("2026-08-05T12:00:00Z"), base);

        assertThatThrownBy(() -> resolver.resolve(withoutProfile))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
