package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.TenantRole;

import java.util.Objects;
import java.util.UUID;

public record IdentityDirectoryEntry(
        UUID tenantId,
        UUID subjectId,
        String username,
        String displayName,
        TenantRole role) {

    public IdentityDirectoryEntry {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        username = requireText(username, "username", 100);
        displayName = requireText(displayName, "displayName", 80);
        Objects.requireNonNull(role, "role");
    }

    private static String requireText(String value, String name, int maxLength) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
