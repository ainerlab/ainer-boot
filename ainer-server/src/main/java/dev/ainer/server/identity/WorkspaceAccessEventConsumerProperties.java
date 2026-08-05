package dev.ainer.server.identity;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.workspace.access-event-consumer")
public class WorkspaceAccessEventConsumerProperties {

    private final boolean enabled;
    private final String trustedPublisherSubject;
    @DurationMin(nanos = 1)
    private final Duration maxFutureSkew;
    @DurationMin(nanos = 1)
    private final Duration propagationSlo;

    public WorkspaceAccessEventConsumerProperties(
            boolean enabled, String trustedPublisherSubject, Duration maxFutureSkew, Duration propagationSlo) {
        this.enabled = enabled;
        this.trustedPublisherSubject = trustedPublisherSubject;
        this.maxFutureSkew = maxFutureSkew != null ? maxFutureSkew : Duration.ofMinutes(5);
        this.propagationSlo = propagationSlo != null ? propagationSlo : Duration.ofSeconds(60);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTrustedPublisherSubject() {
        return trustedPublisherSubject;
    }

    public Duration getMaxFutureSkew() {
        return maxFutureSkew;
    }

    public Duration getPropagationSlo() {
        return propagationSlo;
    }
}
