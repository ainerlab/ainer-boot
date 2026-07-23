package dev.ainer.module.identity.account.application;

import java.util.UUID;

public record ProvisionedIdentity(UUID tenantId, UUID subjectId, String username) {
}
