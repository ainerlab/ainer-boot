package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.account.application.IdentityRepository;
import dev.ainer.module.identity.account.application.IdentityTokenStatus;
import dev.ainer.module.identity.account.application.IdentityTokenStatusRepository;
import dev.ainer.module.identity.account.application.TenantContextEntry;
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
        return Optional.ofNullable(mapper.selectAccountByUsername(normalizedUsername)).map(this::toAccount);
    }

    @Override
    public Optional<IdentityAccount> findAccountBySubjectId(UUID subjectId) {
        return Optional.ofNullable(mapper.selectAccountBySubjectId(subjectId)).map(this::toAccount);
    }

    private IdentityAccount toAccount(IdentityAccountRow row) {
        boolean enabled = "ACTIVE".equals(row.getUserStatus())
                && "ACTIVE".equals(row.getTenantStatus())
                && "ACTIVE".equals(row.getMembershipStatus());
        boolean accountNonLocked = !"LOCKED".equals(row.getUserStatus());
        return new IdentityAccount(
                row.getSubjectId(), row.getUsername(), row.getPasswordHash(), enabled, accountNonLocked,
                row.getTenantId(), Set.of("ROLE_" + row.getRole()));
    }

    @Override
    public Optional<IdentityDirectoryEntry> findActiveDirectoryEntryForUpdate(
            UUID tenantId, UUID subjectId) {
        return Optional.ofNullable(mapper.selectActiveDirectoryEntryForUpdate(tenantId, subjectId))
                .map(this::toDirectoryEntry);
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
    public List<TenantContextEntry> findActiveMembershipsBySubject(UUID subjectId) {
        return mapper.selectActiveMembershipsBySubject(subjectId).stream()
                .map(this::toTenantContextEntry)
                .toList();
    }

    private TenantContextEntry toTenantContextEntry(IdentityMembershipSummaryRow row) {
        return new TenantContextEntry(
                row.getTenantId(),
                row.getTenantCode(),
                row.getTenantName(),
                TenantRole.valueOf(row.getRole()),
                row.isDefaultTenant());
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
    public List<IdentityDirectoryEntry> listMembersByTenant(UUID tenantId, int offset, int limit) {
        return mapper.listMembersByTenant(tenantId, offset, limit).stream()
                .map(this::toDirectoryEntry).toList();
    }

    @Override
    public int countMembersByTenant(UUID tenantId) {
        return mapper.countMembersByTenant(tenantId);
    }

    @Override
    public boolean updateMembershipRole(
            UUID tenantId, UUID subjectId, String newRole, Instant updatedAt) {
        return mapper.updateMembershipRole(tenantId, subjectId, newRole, updatedAt) == 1;
    }

    @Override
    public boolean reactivateMembership(
            UUID tenantId,
            UUID subjectId,
            IdentityStatus expectedStatus,
            String newRole,
            Instant updatedAt) {
        return mapper.reactivateMembership(
                tenantId, subjectId, expectedStatus.name(), newRole, updatedAt) == 1;
    }

    @Override
    public void insertMemberAudit(
            UUID tenantId, UUID actorSubjectId, UUID targetSubjectId,
            String operation, String role, String reasonCode, String requestId, Instant occurredAt) {
        IdentityMemberAuditRow row = new IdentityMemberAuditRow();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setActorSubjectId(actorSubjectId);
        row.setTargetSubjectId(targetSubjectId);
        row.setOperation(operation);
        row.setRole(role);
        row.setReasonCode(reasonCode);
        row.setRequestId(requestId);
        row.setOccurredAt(occurredAt);
        requireSingleRow(mapper.insertMemberAudit(row), "member audit");
    }

    @Override
    public Optional<IdentityDirectoryEntry> findActiveDefaultOwner(
            String tenantCode, String normalizedUsername) {
        return Optional.ofNullable(mapper.selectActiveDefaultOwner(tenantCode, normalizedUsername))
                .map(this::toDirectoryEntry);
    }

    @Override
    public boolean tenantExistsByCode(String tenantCode) {
        return mapper.countTenantByCode(tenantCode) > 0;
    }

    @Override
    public boolean userExistsByUsername(String normalizedUsername) {
        return mapper.countUserByUsername(normalizedUsername) > 0;
    }

    @Override
    public void acquireIdentityLock(String lockKey) {
        mapper.acquireIdentityLock(lockKey);
    }

    @Override
    public boolean openProvisioningReservationExists(
            String tenantCode,
            String normalizedUsername) {
        return mapper.countOpenProvisioningReservation(
                tenantCode, normalizedUsername) > 0;
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
