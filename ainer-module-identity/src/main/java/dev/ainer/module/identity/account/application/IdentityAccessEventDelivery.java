package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityAccessEvent;

import java.util.Objects;

public record IdentityAccessEventDelivery(IdentityAccessEvent event, int attemptCount) {

    public IdentityAccessEventDelivery {
        Objects.requireNonNull(event, "event");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("Access event delivery attempt must be positive");
        }
    }
}
