package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.account.application.IdentityRepository;
import dev.ainer.module.identity.account.application.IdentityTokenStatus;
import dev.ainer.module.identity.account.application.IdentityTokenStatusRepository;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class MybatisIdentityRepository implements IdentityRepository, IdentityTokenStatusRepository {

    private final IdentityMapper mapper;

    public MybatisIdentityRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertTenant(IdentityTenant tenant) {
        requireSingleRow(mapper.insertTenant(tenant), "tenant");
    }

    @Override
    public void insertUser(IdentityUser user) {
        requireSingleRow(mapper.insertUser(user), "user");
    }

    @Override
    public void insertMembership(TenantMembership membership) {
        requireSingleRow(mapper.insertMembership(membership), "membership");
    }

    @Override
    public Optional<IdentityAccount> findAccountByUsername(String normalizedUsername) {
        return Optional.ofNullable(mapper.selectAccountByUsername(normalizedUsername)).map(row -> {
            boolean enabled = "ACTIVE".equals(row.getUserStatus())
                    && "ACTIVE".equals(row.getTenantStatus())
                    && "ACTIVE".equals(row.getMembershipStatus());
            boolean accountNonLocked = !"LOCKED".equals(row.getUserStatus());
            return new IdentityAccount(
                    row.getSubjectId(), row.getUsername(), row.getPasswordHash(), enabled, accountNonLocked,
                    row.getTenantId(), Set.of("ROLE_" + row.getRole()));
        });
    }

    @Override
    public Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(UUID tenantId, UUID subjectId) {
        return Optional.ofNullable(mapper.selectActiveDirectoryEntry(tenantId, subjectId))
                .map(this::toDirectoryEntry);
    }

    @Override
    public List<IdentityDirectoryEntry> searchActiveDirectory(
            UUID tenantId, String likePattern, int limit) {
        return mapper.searchActiveDirectory(tenantId, likePattern, limit)
                .stream().map(this::toDirectoryEntry).toList();
    }

    @Override
    public Optional<IdentityTokenStatus> findTokenStatus(UUID tenantId, UUID subjectId) {
        return Optional.ofNullable(mapper.selectTokenStatus(tenantId, subjectId))
                .map(row -> new IdentityTokenStatus(
                        "ACTIVE".equals(row.getTenantStatus())
                                && "ACTIVE".equals(row.getUserStatus())
                                && "ACTIVE".equals(row.getMembershipStatus()),
                        row.getLatestRevokedAt()));
    }

    @Override
    public Optional<IdentityStatus> findUserStatusForUpdate(UUID subjectId) {
        return Optional.ofNullable(mapper.selectUserStatusForUpdate(subjectId))
                .map(IdentityStatus::valueOf);
    }

    @Override
    public List<UUID> findActiveMembershipTenantIds(UUID subjectId) {
        return mapper.selectActiveMembershipTenantIds(subjectId);
    }

    @Override
    public boolean updateUserStatus(
            UUID subjectId, IdentityStatus expectedStatus, IdentityStatus newStatus, Instant updatedAt) {
        return mapper.updateUserStatus(
                subjectId, expectedStatus.name(), newStatus.name(), updatedAt) == 1;
    }

    @Override
    public Optional<TenantMembership> findMembershipForUpdate(UUID tenantId, UUID subjectId) {
        return Optional.ofNullable(mapper.selectMembershipForUpdate(tenantId, subjectId))
                .map(row -> new TenantMembership(
                        row.getTenantId(),
                        row.getUserId(),
                        TenantRole.valueOf(row.getRole()),
                        row.isDefaultTenant(),
                        IdentityStatus.valueOf(row.getStatus()),
                        row.getJoinedAt(),
                        row.getUpdatedAt()));
    }

    @Override
    public boolean updateMembershipStatus(
            UUID tenantId,
            UUID subjectId,
            IdentityStatus expectedStatus,
            IdentityStatus newStatus,
            Instant updatedAt) {
        return mapper.updateMembershipStatus(
                tenantId, subjectId, expectedStatus.name(), newStatus.name(), updatedAt) == 1;
    }

    @Override
    public void insertAccessEvent(IdentityAccessEvent event) {
        IdentityAccessEventRow row = new IdentityAccessEventRow();
        row.setId(event.id());
        row.setEventType(event.type().name());
        row.setTenantId(event.tenantId());
        row.setSubjectId(event.subjectId());
        row.setPayloadVersion(event.payloadVersion());
        row.setOccurredAt(event.occurredAt());
        row.setPublicationStatus("PENDING");
        row.setAttemptCount(0);
        requireSingleRow(mapper.insertAccessEvent(row), "access event");
    }

    private IdentityDirectoryEntry toDirectoryEntry(IdentityDirectoryRow row) {
        return new IdentityDirectoryEntry(
                row.getTenantId(),
                row.getSubjectId(),
                row.getUsername(),
                row.getDisplayName(),
                TenantRole.valueOf(row.getRole()));
    }

    private void requireSingleRow(int affectedRows, String aggregate) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Identity " + aggregate + " insert affected an unexpected number of rows");
        }
    }
}
