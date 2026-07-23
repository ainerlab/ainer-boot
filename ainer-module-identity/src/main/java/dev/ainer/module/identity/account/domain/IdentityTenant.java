package dev.ainer.module.identity.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record IdentityTenant(
        UUID id,
        String code,
        String name,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    public IdentityTenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("tenant code is invalid");
        }
        name = requireText(name, 2, 80, "tenant name");
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
