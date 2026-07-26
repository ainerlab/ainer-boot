package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.TenantRole;

import java.util.UUID;

public record AddTenantMemberCommand(
        String username,
        UUID subjectId,
        TenantRole role,
        String reasonCode) {
}
