package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Greenfield Identity foundation domain (S1.2 spine). Covers HumanAccount → HumanSubjectRef linkage,
 * account/login status semantics and the binding validation invariants mandated by ADR-0033 Greenfield §3-§5.
 */
class IdentityFoundationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://ainer.example/auth");
    private static final Instant LINKED_AT = Instant.parse("2026-08-04T10:00:00Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-08-04T09:59:00Z");

    @Test
    void humanAccountProjectsToAuthorityQualifiedSubjectRef() {
        UUID accountId = UUID.fromString("019fcc74-0353-7fc0-8f9a-7b12e31d11e8");
        HumanAccount account = new HumanAccount(accountId, AUTHORITY, AccountStatus.ACTIVE, 0L, LINKED_AT);

        HumanSubjectRef ref = account.toSubjectRef();

        assertThat(ref.authority()).isEqualTo(AUTHORITY);
        assertThat(ref.subjectId()).isEqualTo(accountId.toString());
        assertThat(ref.accountId()).isEqualTo(accountId.toString());
    }

    @Test
    void accountStatusGovernsAuthenticationAndLiveness() {
        assertThat(AccountStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(AccountStatus.LOCKED.canAuthenticate()).isFalse();
        assertThat(AccountStatus.DISABLED.canAuthenticate()).isFalse();
        assertThat(AccountStatus.CLOSED.canAuthenticate()).isFalse();

        assertThat(AccountStatus.ACTIVE.isLive()).isTrue();
        assertThat(AccountStatus.LOCKED.isLive()).isTrue();
        assertThat(AccountStatus.DISABLED.isLive()).isFalse();
        assertThat(AccountStatus.CLOSED.isLive()).isFalse();
    }

    @Test
    void humanAccountRejectsNullAndNegativeEpoch() {
        assertThatThrownBy(() -> new HumanAccount(null, AUTHORITY, AccountStatus.ACTIVE, 0L, LINKED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HumanAccount(UUID.randomUUID(), AUTHORITY, AccountStatus.ACTIVE, -1L, LINKED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginIdentityIsInitiallyUnusedAndBecomesUsed() {
        LoginIdentity fresh = new LoginIdentity(
                UUID.randomUUID(), UUID.randomUUID(), LoginIdentityType.EMAIL,
                "https://ainer.example/auth", "owner@example.com",
                LoginIdentityStatus.ACTIVE, VERIFIED_AT, LINKED_AT, null);

        assertThat(fresh.isActive()).isTrue();
        assertThat(fresh.hasBeenUsed()).isFalse();

        LoginIdentity used = new LoginIdentity(
                fresh.identityId(), fresh.accountId(), fresh.type(),
                fresh.providerAuthority(), fresh.normalizedIdentifier(),
                LoginIdentityStatus.ACTIVE, VERIFIED_AT, LINKED_AT, LINKED_AT.plusSeconds(60));
        assertThat(used.hasBeenUsed()).isTrue();
    }

    @Test
    void loginIdentityRejectsBlankAuthorityAndIdentifier() {
        assertThatThrownBy(() -> new LoginIdentity(
                UUID.randomUUID(), UUID.randomUUID(), LoginIdentityType.USERNAME,
                "  ", "owner", LoginIdentityStatus.ACTIVE, VERIFIED_AT, LINKED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginIdentity(
                UUID.randomUUID(), UUID.randomUUID(), LoginIdentityType.USERNAME,
                "https://ainer.example/auth", "  ", LoginIdentityStatus.ACTIVE, VERIFIED_AT, LINKED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginIdentity(
                UUID.randomUUID(), UUID.randomUUID(), LoginIdentityType.USERNAME,
                "https://ainer.example/auth", "owner", null, VERIFIED_AT, LINKED_AT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void revokedLoginIdentityIsNotActive() {
        LoginIdentity revoked = new LoginIdentity(
                UUID.randomUUID(), UUID.randomUUID(), LoginIdentityType.OIDC,
                "https://corp.example", "sub-42",
                LoginIdentityStatus.REVOKED, VERIFIED_AT, LINKED_AT, null);

        assertThat(revoked.isActive()).isFalse();
    }

    @Test
    void activeCredentialCannotCarryRotatedAt() {
        assertThatThrownBy(() -> new Credential(
                UUID.randomUUID(), UUID.randomUUID(), CredentialType.PASSWORD,
                "{bcrypt}hash", CredentialStatus.ACTIVE, LINKED_AT, LINKED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialRejectsBlankMaterial() {
        assertThatThrownBy(() -> new Credential(
                UUID.randomUUID(), UUID.randomUUID(), CredentialType.PASSWORD,
                "  ", CredentialStatus.ACTIVE, LINKED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialMaterialIsActiveOnlyWhenStatusActive() {
        Credential active = new Credential(
                UUID.randomUUID(), UUID.randomUUID(), CredentialType.WEBAUTHN_PUBLIC_KEY,
                "public-key-material", CredentialStatus.ACTIVE, LINKED_AT, null);
        Credential revoked = new Credential(
                UUID.randomUUID(), UUID.randomUUID(), CredentialType.PASSWORD,
                "{bcrypt}hash", CredentialStatus.REVOKED, LINKED_AT, LINKED_AT.plusSeconds(60));

        assertThat(active.isActive()).isTrue();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.rotatedAt()).isEqualTo(LINKED_AT.plusSeconds(60));
    }

    @Test
    void revokedCredentialKeepsMaterialForAudit() {
        Credential revoked = new Credential(
                UUID.randomUUID(), UUID.randomUUID(), CredentialType.PASSWORD,
                "{bcrypt}hash", CredentialStatus.REVOKED, LINKED_AT, LINKED_AT.plusSeconds(60));

        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.credentialData()).isEqualTo("{bcrypt}hash");
        assertThat(revoked.rotatedAt()).isEqualTo(LINKED_AT.plusSeconds(60));
    }

    @Test
    void humanProfileExposesNullablePresentationFields() {
        HumanProfile full = new HumanProfile(
                UUID.randomUUID(), "Ainer User", "https://cdn.example/avatar.png", LINKED_AT);
        HumanProfile minimal = new HumanProfile(UUID.randomUUID(), null, null, LINKED_AT);

        assertThat(full.displayName()).isEqualTo("Ainer User");
        assertThat(full.avatarUrl()).isEqualTo("https://cdn.example/avatar.png");
        assertThat(minimal.displayName()).isNull();
        assertThat(minimal.avatarUrl()).isNull();
        assertThat(minimal.updatedAt()).isEqualTo(LINKED_AT);
    }
}
