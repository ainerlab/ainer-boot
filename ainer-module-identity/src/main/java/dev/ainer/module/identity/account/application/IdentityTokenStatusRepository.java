package dev.ainer.module.identity.account.application;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface IdentityTokenStatusRepository {

    Optional<IdentityTokenStatus> findTokenStatus(UUID tenantId, UUID subjectId);
}
