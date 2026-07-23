package dev.ainer.server.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventConsumer;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventResult;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

@RestController
@RequestMapping("/internal/identity/access-events")
@ConditionalOnProperty(
        prefix = "ainer.workspace.access-event-consumer",
        name = "enabled",
        havingValue = "true")
public class WorkspaceIdentityAccessEventController {

    private static final String EVENT_PUBLISH = "SCOPE_identity.access-events.publish";

    private final WorkspaceIdentityAccessEventConsumer consumer;
    private final WorkspaceAccessEventConsumerSettings settings;
    private final Clock clock;
    private final Counter receivedCounter;
    private final Counter duplicateCounter;
    private final Counter revokedMembershipCounter;
    private final Timer propagationTimer;

    public WorkspaceIdentityAccessEventController(
            WorkspaceIdentityAccessEventConsumer consumer,
            WorkspaceAccessEventConsumerSettings settings,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.consumer = consumer;
        this.settings = settings;
        this.clock = clock;
        this.receivedCounter = meterRegistry.counter("ainer.workspace.identity.access.events.received");
        this.duplicateCounter = meterRegistry.counter("ainer.workspace.identity.access.events.duplicates");
        this.revokedMembershipCounter = meterRegistry.counter(
                "ainer.workspace.identity.access.memberships.revoked");
        this.propagationTimer = Timer.builder(
                        "ainer.workspace.identity.access.events.propagation")
                .serviceLevelObjectives(settings.propagationSlo())
                .register(meterRegistry);
    }

    @PostMapping
    public ApiResponse<WorkspaceIdentityAccessEventResponse> consume(
            @Valid @RequestBody WorkspaceIdentityAccessEventRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        requireTrustedPublisher(authentication);
        if (body.occurredAt().isAfter(clock.instant().plus(settings.maxFutureSkew()))) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }

        WorkspaceIdentityAccessEventResult result = consumer.consume(body.toEvent());
        receivedCounter.increment();
        if (result.duplicate()) {
            duplicateCounter.increment();
        } else {
            revokedMembershipCounter.increment(result.affectedMemberships());
            Duration propagation = Duration.between(body.occurredAt(), clock.instant());
            propagationTimer.record(propagation.isNegative() ? Duration.ZERO : propagation);
        }
        return ApiResponse.success(
                WorkspaceIdentityAccessEventResponse.from(body.eventId(), result),
                RequestIds.currentOrCreate(request));
    }

    private void requireTrustedPublisher(Authentication authentication) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(EVENT_PUBLISH);
        if (!settings.trustedPublisherSubject().equals(service.serviceId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
