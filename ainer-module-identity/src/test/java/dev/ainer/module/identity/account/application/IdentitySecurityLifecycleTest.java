package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityAccessEventType;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentitySecurityLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-07-23T01:00:00Z");

    private InMemoryIdentityRepository repository;
    private IdentityDirectoryService directoryService;
    private IdentityAccessLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryIdentityRepository();
        directoryService = new IdentityDirectoryService(repository);
        lifecycleService = new IdentityAccessLifecycleService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void directoryReturnsSafeProjectionAndEscapesSqlWildcards() {
        UUID tenantId = UUID.randomUUID();
        IdentityDirectoryEntry entry = new IdentityDirectoryEntry(
                tenantId, UUID.randomUUID(), "member@example.com", "Member", TenantRole.MEMBER);
        repository.directoryEntries.add(entry);

        assertThat(directoryService.findActiveMember(tenantId, entry.subjectId()))
                .contains(entry);
        assertThat(directoryService.searchActiveMembers(tenantId, "a_%!", 10))
                .containsExactly(entry);
        assertThat(repository.lastLikePattern).isEqualTo("%a!_!%!!%");
        assertError(
                () -> directoryService.searchActiveMembers(tenantId, "x", 10),
                IdentityErrorCode.INVALID_DIRECTORY_QUERY);
    }

    @Test
    void disableUserCreatesOneEventPerActiveTenantAndIsIdempotent() {
        UUID subjectId = UUID.randomUUID();
        UUID tenantOne = UUID.randomUUID();
        UUID tenantTwo = UUID.randomUUID();
        repository.userStatuses.put(subjectId, IdentityStatus.ACTIVE);
        repository.memberships.put(
                new MemberKey(tenantOne, subjectId), membership(tenantOne, subjectId, TenantRole.OWNER));
        repository.memberships.put(
                new MemberKey(tenantTwo, subjectId), membership(tenantTwo, subjectId, TenantRole.MEMBER));

        assertThat(lifecycleService.disableUser(subjectId)).isEqualTo(2);
        assertThat(repository.userStatuses.get(subjectId)).isEqualTo(IdentityStatus.DISABLED);
        assertThat(repository.events)
                .extracting(IdentityAccessEvent::type)
                .containsOnly(IdentityAccessEventType.IDENTITY_USER_DISABLED);
        assertThat(repository.events)
                .extracting(IdentityAccessEvent::tenantId)
                .containsExactlyInAnyOrder(tenantOne, tenantTwo);
        assertThat(lifecycleService.disableUser(subjectId)).isZero();
        assertThat(repository.events).hasSize(2);
    }

    @Test
    void membershipRevocationWritesEventAndOwnerRequiresTransfer() {
        UUID tenantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        repository.memberships.put(
                new MemberKey(tenantId, memberId), membership(tenantId, memberId, TenantRole.MEMBER));
        repository.memberships.put(
                new MemberKey(tenantId, ownerId), membership(tenantId, ownerId, TenantRole.OWNER));

        assertThat(lifecycleService.revokeMembership(tenantId, memberId)).isTrue();
        assertThat(repository.memberships.get(new MemberKey(tenantId, memberId)).status())
                .isEqualTo(IdentityStatus.DISABLED);
        assertThat(repository.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(IdentityAccessEventType.IDENTITY_MEMBERSHIP_REVOKED);
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
        assertError(
                () -> lifecycleService.revokeMembership(tenantId, ownerId),
                IdentityErrorCode.OWNER_REVOCATION_REQUIRES_TRANSFER);
    }

    @Test
    void concurrentStateChangeReturnsStableConflict() {
        UUID subjectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        repository.userStatuses.put(subjectId, IdentityStatus.ACTIVE);
        repository.memberships.put(
                new MemberKey(tenantId, subjectId), membership(tenantId, subjectId, TenantRole.MEMBER));
        repository.failUserUpdate = true;

        assertError(
                () -> lifecycleService.disableUser(subjectId),
                IdentityErrorCode.ACCESS_STATE_CONFLICT);
        assertThat(repository.events).isEmpty();
    }

    private static TenantMembership membership(UUID tenantId, UUID subjectId, TenantRole role) {
        Instant joinedAt = NOW.minusSeconds(60);
        return new TenantMembership(
                tenantId, subjectId, role, true, IdentityStatus.ACTIVE, joinedAt, joinedAt);
    }

    private static void assertError(Runnable invocation, IdentityErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static final class InMemoryIdentityRepository implements IdentityRepository {

        private final Map<UUID, IdentityStatus> userStatuses = new LinkedHashMap<>();
        private final Map<MemberKey, TenantMembership> memberships = new LinkedHashMap<>();
        private final List<IdentityDirectoryEntry> directoryEntries = new ArrayList<>();
        private final List<IdentityAccessEvent> events = new ArrayList<>();
        private boolean failUserUpdate;
        private String lastLikePattern;

        @Override
        public void insertTenant(IdentityTenant tenant) {
        }

        @Override
        public void insertUser(IdentityUser user) {
            userStatuses.put(user.id(), user.status());
        }

        @Override
        public void insertMembership(TenantMembership membership) {
            memberships.put(new MemberKey(membership.tenantId(), membership.userId()), membership);
        }

        @Override
        public Optional<IdentityAccount> findAccountByUsername(String normalizedUsername) {
            return Optional.empty();
        }

        @Override
        public Optional<IdentityAccount> findAccountBySubjectId(UUID subjectId) {
            return Optional.empty();
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(
                UUID tenantId, UUID subjectId) {
            return directoryEntries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId))
                    .filter(entry -> entry.subjectId().equals(subjectId))
                    .findFirst();
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntryForUpdate(
                UUID tenantId, UUID subjectId) {
            return findActiveDirectoryEntry(tenantId, subjectId);
        }

        @Override
        public List<IdentityDirectoryEntry> searchActiveDirectory(
                UUID tenantId, String likePattern, int limit) {
            lastLikePattern = likePattern;
            return directoryEntries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<IdentityStatus> findUserStatusForUpdate(UUID subjectId) {
            return Optional.ofNullable(userStatuses.get(subjectId));
        }

        @Override
        public List<UUID> findActiveMembershipTenantIds(UUID subjectId) {
            return memberships.values().stream()
                    .filter(membership -> membership.userId().equals(subjectId))
                    .filter(membership -> membership.status() == IdentityStatus.ACTIVE)
                    .map(TenantMembership::tenantId)
                    .toList();
        }

        @Override
        public boolean updateUserStatus(
                UUID subjectId,
                IdentityStatus expectedStatus,
                IdentityStatus newStatus,
                Instant updatedAt) {
            if (failUserUpdate || userStatuses.get(subjectId) != expectedStatus) {
                return false;
            }
            userStatuses.put(subjectId, newStatus);
            return true;
        }

        @Override
        public Optional<TenantMembership> findMembershipForUpdate(UUID tenantId, UUID subjectId) {
            return Optional.ofNullable(memberships.get(new MemberKey(tenantId, subjectId)));
        }

        @Override
        public boolean updateMembershipStatus(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                IdentityStatus newStatus,
                Instant updatedAt) {
            MemberKey key = new MemberKey(tenantId, subjectId);
            TenantMembership current = memberships.get(key);
            if (current == null || current.status() != expectedStatus) {
                return false;
            }
            memberships.put(key, new TenantMembership(
                    current.tenantId(), current.userId(), current.role(), current.defaultTenant(),
                    newStatus, current.joinedAt(), updatedAt));
            return true;
        }

        @Override
        public List<IdentityDirectoryEntry> listMembersByTenant(UUID tenantId, int offset, int limit) {
            return directoryEntries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public int countMembersByTenant(UUID tenantId) {
            return (int) directoryEntries.stream()
                    .filter(entry -> entry.tenantId().equals(tenantId))
                    .count();
        }

        @Override
        public boolean updateMembershipRole(
                UUID tenantId, UUID subjectId, String newRole, Instant updatedAt) {
            return false;
        }

        @Override
        public boolean reactivateMembership(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                String newRole,
                Instant updatedAt) {
            return false;
        }

        @Override
        public void insertMemberAudit(
                UUID tenantId,
                UUID actorSubjectId,
                UUID targetSubjectId,
                String operation,
                String role,
                String reasonCode,
                String requestId,
                Instant occurredAt) {
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDefaultOwner(
                String tenantCode, String normalizedUsername) {
            return Optional.empty();
        }

        @Override
        public boolean tenantExistsByCode(String tenantCode) {
            return false;
        }

        @Override
        public boolean userExistsByUsername(String normalizedUsername) {
            return false;
        }

        @Override
        public void acquireTenantBootstrapLock(String tenantCode, String normalizedUsername) {
        }

        @Override
        public void insertAccessEvent(IdentityAccessEvent event) {
            events.add(event);
        }
    }

    private record MemberKey(UUID tenantId, UUID subjectId) {
    }
}
