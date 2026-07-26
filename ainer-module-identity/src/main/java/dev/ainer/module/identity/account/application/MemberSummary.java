package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.TenantRole;

import java.util.UUID;

public record MemberSummary(UUID subjectId, String username, String displayName, TenantRole role) {

    public static MemberSummary from(IdentityDirectoryEntry entry) {
        return new MemberSummary(entry.subjectId(), entry.username(), entry.displayName(), entry.role());
    }
}
