package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxPage;
import dev.ainer.module.identity.account.application.IdentityAccessEventRecoveryService;
import dev.ainer.module.identity.account.application.IdentityAccessEventReplayRequest;
import dev.ainer.security.service.AuthenticatedService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityAccessEventRecoveryControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T06:00:00Z");
    private static final UUID TENANT_ID = UUID.randomUUID();

    private final StubRecoveryService service = new StubRecoveryService();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final IdentityAccessEventRecoveryController controller =
            new IdentityAccessEventRecoveryController(
                    service,
                    new IdentityAccessEventRecoverySettings(Duration.ofMinutes(15), 10),
                    meterRegistry);

    @Test
    void tenantBoundRequestAndIndependentApprovalUseServiceSubjects() {
        UUID eventId = UUID.randomUUID();
        var requested = controller.requestReplay(
                TENANT_ID,
                new IdentityAccessEventReplayRequestBody(eventId, "INC-IDENTITY-1"),
                authentication(
                        "operator:request", TENANT_ID.toString(), AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_identity.access-events.replay.request"),
                new MockHttpServletRequest());
        var approved = controller.approve(
                TENANT_ID,
                requested.data().requestId(),
                authentication(
                        "operator:approve", TENANT_ID.toString(), AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_identity.access-events.replay.approve"),
                new MockHttpServletRequest());

        assertThat(requested.data().status()).isEqualTo("REQUESTED");
        assertThat(approved.data().status()).isEqualTo("EXECUTED");
        assertThat(service.requester).isEqualTo("operator:request");
        assertThat(service.approver).isEqualTo("operator:approve");
        assertThat(meterRegistry.counter("ainer.identity.access.events.replay.requested").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("ainer.identity.access.events.replay.executed").count())
                .isEqualTo(1);
    }

    @Test
    void rejectsUserMissingScopeAndCrossTenantService() {
        IdentityAccessEventReplayRequestBody body = new IdentityAccessEventReplayRequestBody(
                UUID.randomUUID(), "INC-IDENTITY-2");

        assertForbidden(() -> controller.requestReplay(
                TENANT_ID,
                body,
                authentication(
                        "operator:user", TENANT_ID.toString(), "USER",
                        "SCOPE_identity.access-events.replay.request"),
                new MockHttpServletRequest()));
        assertForbidden(() -> controller.requestReplay(
                TENANT_ID,
                body,
                authentication(
                        "operator:no-scope", TENANT_ID.toString(), AuthenticatedService.SERVICE_ACTOR_TYPE),
                new MockHttpServletRequest()));
        assertForbidden(() -> controller.requestReplay(
                TENANT_ID,
                body,
                authentication(
                        "operator:cross", UUID.randomUUID().toString(), AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_identity.access-events.replay.request"),
                new MockHttpServletRequest()));
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.FORBIDDEN));
    }

    private Authentication authentication(
            String subject, String tenantId, String actorType, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("tenant_id", tenantId)
                .claim(AuthenticatedService.ACTOR_TYPE_CLAIM, actorType)
                .build();
        return new JwtAuthenticationToken(
                jwt,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static final class StubRecoveryService extends IdentityAccessEventRecoveryService {
        private String requester;
        private String approver;
        private IdentityAccessEventReplayRequest request;

        private StubRecoveryService() {
            super(null, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public IdentityAccessEventOutboxPage findExhausted(
                UUID tenantId, int page, int size, int maxAttempts) {
            return new IdentityAccessEventOutboxPage(List.of(), page, size, 0);
        }

        @Override
        public IdentityAccessEventReplayRequest requestReplay(
                String requesterServiceId,
                UUID tenantId,
                UUID eventId,
                String incidentReference,
                Duration approvalTtl,
                int maxAttempts) {
            requester = requesterServiceId;
            request = new IdentityAccessEventReplayRequest(
                    UUID.randomUUID(), eventId, tenantId, requesterServiceId, null,
                    incidentReference, "REQUESTED", NOW, NOW.plus(approvalTtl), null);
            return request;
        }

        @Override
        public IdentityAccessEventReplayRequest approveAndExecute(
                String approverServiceId, UUID tenantId, UUID requestId, int maxAttempts) {
            approver = approverServiceId;
            return new IdentityAccessEventReplayRequest(
                    request.id(), request.eventId(), request.tenantId(), request.requestedBy(), approver,
                    request.incidentReference(), "EXECUTED", request.requestedAt(), request.expiresAt(), NOW);
        }
    }
}
