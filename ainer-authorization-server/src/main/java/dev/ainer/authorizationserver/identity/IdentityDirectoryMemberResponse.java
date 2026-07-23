package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;

import java.util.UUID;

public record IdentityDirectoryMemberResponse(
        UUID tenantId,
        UUID subjectId,
        String username,
        String displayName,
        String role) {

    static IdentityDirectoryMemberResponse from(IdentityDirectoryEntry entry) {
        return new IdentityDirectoryMemberResponse(
                entry.tenantId(),
                entry.subjectId(),
                entry.username(),
                entry.displayName(),
                entry.role().name());
    }
}
