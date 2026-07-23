package dev.ainer.module.identity.account.application;

import java.time.Instant;

public record IdentityAccessEventOutboxStatus(
        long pending,
        long failed,
        long exhausted,
        long published,
        Instant oldestReadyAt) {
}
