package dev.ainer.server.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEvent;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventConsumer;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventRepository;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventType;
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

class WorkspaceIdentityAccessEventControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T02:00:00Z");
    private static final String TRUSTED_PUBLISHER = "ainer-identity-relay";

    private final InMemoryEventRepository repository = new InMemoryEventRepository();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WorkspaceIdentityAccessEventController controller =
            new WorkspaceIdentityAccessEventController(
                    new WorkspaceIdentityAccessEventConsumer(
                            repository, Clock.fixed(NOW, ZoneOffset.UTC)),
                    new WorkspaceAccessEventConsumerSettings(
                            TRUSTED_PUBLISHER, Duration.ofMinutes(5), Duration.ofSeconds(60)),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    meterRegistry);

    @Test
    void acceptsOnlyTrustedServicePublisherAndReportsDuplicates() {
        UUID eventId = UUID.randomUUID();
        WorkspaceIdentityAccessEventRequest request = request(eventId, NOW);
        Authentication publisher = authentication(
                TRUSTED_PUBLISHER,
                AuthenticatedService.SERVICE_ACTOR_TYPE,
                "SCOPE_identity.access-events.publish");

        ApiResponse<WorkspaceIdentityAccessEventResponse> first = controller.consume(
                request, publisher, new MockHttpServletRequest());
        ApiResponse<WorkspaceIdentityAccessEventResponse> duplicate = controller.consume(
                request, publisher, new MockHttpServletRequest());

        assertThat(first.data()).isEqualTo(new WorkspaceIdentityAccessEventResponse(eventId, false, 2));
        assertThat(duplicate.data()).isEqualTo(new WorkspaceIdentityAccessEventResponse(eventId, true, 2));
        assertThat(meterRegistry.counter("ainer.workspace.identity.access.events.received").count())
                .isEqualTo(2);
        assertThat(meterRegistry.counter("ainer.workspace.identity.access.events.duplicates").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter(
                "ainer.workspace.identity.access.memberships.revoked").count()).isEqualTo(2);
        assertThat(meterRegistry.timer(
                "ainer.workspace.identity.access.events.propagation").count()).isEqualTo(1);
    }

    @Test
    void rejectsUserTokenEvenWhenItContainsPublisherScope() {
        Authentication user = authentication(
                TRUSTED_PUBLISHER,
                "USER",
                "SCOPE_identity.access-events.publish");

        assertThatThrownBy(() -> controller.consume(
                request(UUID.randomUUID(), NOW), user, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsUnexpectedPublisherAndFutureEvent() {
        Authentication unexpected = authentication(
                "another-service",
                AuthenticatedService.SERVICE_ACTOR_TYPE,
                "SCOPE_identity.access-events.publish");
        Authentication trusted = authentication(
                TRUSTED_PUBLISHER,
                AuthenticatedService.SERVICE_ACTOR_TYPE,
                "SCOPE_identity.access-events.publish");

        assertThatThrownBy(() -> controller.consume(
                request(UUID.randomUUID(), NOW), unexpected, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> controller.consume(
                request(UUID.randomUUID(), NOW.plus(Duration.ofMinutes(6))),
                trusted,
                new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.INVALID_REQUEST));
    }

    private WorkspaceIdentityAccessEventRequest request(UUID eventId, Instant occurredAt) {
        return new WorkspaceIdentityAccessEventRequest(
                eventId,
                WorkspaceIdentityAccessEventType.IDENTITY_USER_DISABLED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                occurredAt);
    }

    private Authentication authentication(String subject, String actorType, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim(AuthenticatedService.ACTOR_TYPE_CLAIM, actorType)
                .build();
        return new JwtAuthenticationToken(
                jwt,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static final class InMemoryEventRepository
            implements WorkspaceIdentityAccessEventRepository {

        private UUID receivedEventId;

        @Override
        public boolean insertReceipt(WorkspaceIdentityAccessEvent event, Instant receivedAt) {
            if (event.eventId().equals(receivedEventId)) {
                return false;
            }
            receivedEventId = event.eventId();
            return true;
        }

        @Override
        public int revokeExistingMemberships(
                WorkspaceIdentityAccessEvent event, Instant receivedAt) {
            return 2;
        }

        @Override
        public void recordAffectedMemberships(UUID eventId, int affectedMemberships) {
        }

        @Override
        public int findAffectedMemberships(UUID eventId) {
            return 2;
        }
    }
}
