package dev.ainer.module.identity.account.application;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class IdentityTokenStatusService {

    private final IdentityTokenStatusRepository repository;

    public IdentityTokenStatusService(IdentityTokenStatusRepository repository) {
        this.repository = repository;
    }

    public boolean isAccessTokenActive(UUID tenantId, UUID subjectId, Instant issuedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        return repository.findTokenStatus(tenantId, subjectId)
                .map(status -> status.permits(issuedAt))
                .orElse(false);
    }
}
