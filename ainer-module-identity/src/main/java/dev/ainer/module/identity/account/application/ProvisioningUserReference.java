package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityStatus;

import java.util.UUID;

public record ProvisioningUserReference(
        UUID subjectId,
        String username,
        String displayName,
        IdentityStatus status) {
}
