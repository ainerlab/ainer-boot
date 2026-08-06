package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Display profile of a {@link HumanAccount} (ADR-0033 Greenfield §4, execution plan 缺口 A).
 *
 * <p>A 0:1 attribute aggregate: an account has at most one profile, and a profile never exists without its
 * account. Only presentation-grade attributes live here (display name, avatar URL); identity-critical facts
 * stay on the account and its LoginIdentity bindings.
 */
public record HumanProfile(
        UUID accountId,
        @Nullable String displayName,
        @Nullable String avatarUrl,
        Instant updatedAt) {

    public HumanProfile {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}