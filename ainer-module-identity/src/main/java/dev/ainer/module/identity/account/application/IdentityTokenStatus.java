package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Objects;

public record IdentityTokenStatus(boolean currentAccessActive, Instant latestRevokedAt) {

    public boolean permits(Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt");
        return currentAccessActive
                && (latestRevokedAt == null || issuedAt.isAfter(latestRevokedAt));
    }
}
