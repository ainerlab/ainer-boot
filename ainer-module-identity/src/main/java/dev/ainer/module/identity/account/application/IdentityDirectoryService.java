package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityDirectoryService {

    private final IdentityRepository repository;

    public IdentityDirectoryService(IdentityRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdentityDirectoryEntry> findActiveMember(UUID tenantId, UUID subjectId) {
        if (tenantId == null || subjectId == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_IDENTITY_REFERENCE);
        }
        return repository.findActiveDirectoryEntry(tenantId, subjectId);
    }

    @Transactional(readOnly = true)
    public List<IdentityDirectoryEntry> searchActiveMembers(UUID tenantId, String query, int limit) {
        if (tenantId == null || query == null || limit < 1 || limit > 50) {
            throw new BusinessException(IdentityErrorCode.INVALID_DIRECTORY_QUERY);
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new BusinessException(IdentityErrorCode.INVALID_DIRECTORY_QUERY);
        }
        return repository.searchActiveDirectory(tenantId, likeContains(normalized), limit);
    }

    private String likeContains(String value) {
        return "%" + value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
