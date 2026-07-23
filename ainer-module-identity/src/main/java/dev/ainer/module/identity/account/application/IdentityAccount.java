package dev.ainer.module.identity.account.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record IdentityAccount(
        UUID subjectId,
        String username,
        String passwordHash,
        boolean enabled,
        boolean accountNonLocked,
        UUID tenantId,
        Set<String> roles) {

    public IdentityAccount {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(tenantId, "tenantId");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }
}
