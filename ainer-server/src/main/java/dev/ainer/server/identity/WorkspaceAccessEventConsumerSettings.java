package dev.ainer.server.identity;

import java.time.Duration;

record WorkspaceAccessEventConsumerSettings(
        String trustedPublisherSubject,
        Duration maxFutureSkew,
        Duration propagationSlo) {
}
