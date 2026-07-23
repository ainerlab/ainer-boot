package dev.ainer.module.identity.account.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityTokenStatusServiceTest {

    private static final Instant REVOKED_AT = Instant.parse("2026-07-23T02:00:00Z");
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUBJECT_ID = UUID.randomUUID();

    @Test
    void missingOrCurrentlyInactiveIdentityFailsClosed() {
        IdentityTokenStatusService missing = new IdentityTokenStatusService(
                (tenantId, subjectId) -> Optional.empty());
        IdentityTokenStatusService inactive = new IdentityTokenStatusService(
                (tenantId, subjectId) -> Optional.of(new IdentityTokenStatus(false, null)));

        assertThat(missing.isAccessTokenActive(TENANT_ID, SUBJECT_ID, REVOKED_AT)).isFalse();
        assertThat(inactive.isAccessTokenActive(TENANT_ID, SUBJECT_ID, REVOKED_AT)).isFalse();
    }

    @Test
    void tokenAtOrBeforeLatestRevocationIsInactiveAndNewerTokenIsActive() {
        IdentityTokenStatusService service = new IdentityTokenStatusService(
                (tenantId, subjectId) -> Optional.of(new IdentityTokenStatus(true, REVOKED_AT)));

        assertThat(service.isAccessTokenActive(
                TENANT_ID, SUBJECT_ID, REVOKED_AT.minusSeconds(1))).isFalse();
        assertThat(service.isAccessTokenActive(TENANT_ID, SUBJECT_ID, REVOKED_AT)).isFalse();
        assertThat(service.isAccessTokenActive(
                TENANT_ID, SUBJECT_ID, REVOKED_AT.plusNanos(1))).isTrue();
    }
}
