package dev.ainer.server.identity;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.workspace.access-event-consumer")
public class WorkspaceAccessEventConsumerProperties {

    private boolean enabled;
    private String trustedPublisherSubject;
    @Positive
    private Duration maxFutureSkew = Duration.ofMinutes(5);
    @Positive
    private Duration propagationSlo = Duration.ofSeconds(60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTrustedPublisherSubject() {
        return trustedPublisherSubject;
    }

    public void setTrustedPublisherSubject(String trustedPublisherSubject) {
        this.trustedPublisherSubject = trustedPublisherSubject;
    }

    public Duration getMaxFutureSkew() {
        return maxFutureSkew;
    }

    public void setMaxFutureSkew(Duration maxFutureSkew) {
        this.maxFutureSkew = maxFutureSkew;
    }

    public Duration getPropagationSlo() {
        return propagationSlo;
    }

    public void setPropagationSlo(Duration propagationSlo) {
        this.propagationSlo = propagationSlo;
    }
}
