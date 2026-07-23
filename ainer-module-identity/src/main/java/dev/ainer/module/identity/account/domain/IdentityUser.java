package dev.ainer.module.identity.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record IdentityUser(
        UUID id,
        String username,
        String passwordHash,
        String displayName,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._@-]{2,99}");

    public IdentityUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("username is invalid");
        }
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new IllegalArgumentException("password hash is invalid");
        }
        displayName = requireText(displayName, 1, 80, "display name");
    }

    private static String requireText(String value, int min, int max, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        String normalized = value.trim();
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
