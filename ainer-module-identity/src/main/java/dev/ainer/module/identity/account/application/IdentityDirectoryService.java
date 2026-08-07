package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.foundation.HumanAccount;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityDirectoryService {

    private final IdentityRepository repository;
    private final HumanAccountRepository humanAccountRepository;

    public IdentityDirectoryService(IdentityRepository repository) {
        this(repository, null);
    }

    @Autowired
    public IdentityDirectoryService(
            IdentityRepository repository, HumanAccountRepository humanAccountRepository) {
        this.repository = repository;
        this.humanAccountRepository = humanAccountRepository;
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

    @Transactional(readOnly = true)
    public Optional<HumanAccount> findActiveHumanAccount(UUID accountId) {
        if (accountId == null || humanAccountRepository == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_IDENTITY_REFERENCE);
        }
        return humanAccountRepository.findByAccountId(accountId)
                .filter(account -> account.status().canAuthenticate());
    }

    private String likeContains(String value) {
        return "%" + value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
