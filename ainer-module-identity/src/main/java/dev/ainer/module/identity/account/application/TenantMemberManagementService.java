package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 租户内成员管理。授权同时要求 USER actor、显式 capability scope、可信 tenant 投影和数据库中的
 * ACTIVE OWNER/ADMIN 关系；任一条件都不能替代其他条件。
 */
@Service
public class TenantMemberManagementService {

    private static final String READ_AUTHORITY = "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_READ;
    private static final String WRITE_AUTHORITY = "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_WRITE;
    private static final Pattern SAFE_AUDIT_REFERENCE =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final IdentityRepository repository;
    private final Clock clock;

    public TenantMemberManagementService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MemberPage listMembers(AuthenticatedActor actor, UUID tenantId, int page, int size) {
        requireCallerManager(actor, tenantId, READ_AUTHORITY, false);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
        long offset = Math.multiplyExact((long) page - 1L, size);
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
        int total = repository.countMembersByTenant(tenantId);
        List<MemberSummary> members = repository.listMembersByTenant(tenantId, (int) offset, size).stream()
                .map(MemberSummary::from)
                .toList();
        return new MemberPage(members, page, size, total);
    }

    @Transactional
    public MemberSummary addMember(
            AuthenticatedActor actor,
            UUID tenantId,
            AddTenantMemberCommand command,
            String requestId) {
        UUID callerSubjectId = requireCallerManager(actor, tenantId, WRITE_AUTHORITY, true);
        requireAuditReference(command == null ? null : command.reasonCode());
        requireAuditReference(requestId);
        TenantRole role = command.role();
        requireRoleAssignable(role);
        IdentityAccount target = resolveTarget(command);
        if (!target.enabled() || !target.accountNonLocked()) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND);
        }
        IdentityStatus userStatus = repository.findUserStatusForUpdate(target.subjectId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
        if (userStatus != IdentityStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND);
        }

        Instant now = clock.instant();
        TenantMembership existing = repository.findMembershipForUpdate(tenantId, target.subjectId()).orElse(null);
        String operation;
        if (existing == null) {
            try {
                repository.insertMembership(new TenantMembership(
                        tenantId, target.subjectId(), role, false, IdentityStatus.ACTIVE, now, now));
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(IdentityErrorCode.MEMBER_ALREADY_EXISTS);
            }
            operation = "ADDED";
        } else {
            if (existing.status() == IdentityStatus.ACTIVE) {
                throw new BusinessException(IdentityErrorCode.MEMBER_ALREADY_EXISTS);
            }
            if (existing.status() != IdentityStatus.DISABLED || existing.role() == TenantRole.OWNER) {
                throw new BusinessException(IdentityErrorCode.MEMBER_STATE_CONFLICT);
            }
            if (!repository.reactivateMembership(
                    tenantId, target.subjectId(), IdentityStatus.DISABLED, role.name(), now)) {
                throw new BusinessException(IdentityErrorCode.MEMBER_STATE_CONFLICT);
            }
            operation = "REACTIVATED";
        }
        audit(
                tenantId, callerSubjectId, target.subjectId(), operation, role.name(),
                command.reasonCode(), requestId, now);
        return activeMember(tenantId, target.subjectId());
    }

    @Transactional
    public MemberSummary changeMemberRole(
            AuthenticatedActor actor,
            UUID tenantId,
            UUID targetSubjectId,
            TenantRole newRole,
            String reasonCode,
            String requestId) {
        UUID callerSubjectId = requireCallerManager(actor, tenantId, WRITE_AUTHORITY, true);
        requireRoleAssignable(newRole);
        requireAuditReference(reasonCode);
        requireAuditReference(requestId);
        IdentityDirectoryEntry target = repository.findActiveDirectoryEntryForUpdate(tenantId, targetSubjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.MEMBERSHIP_NOT_FOUND));
        if (target.role() == TenantRole.OWNER) {
            throw new BusinessException(IdentityErrorCode.CANNOT_MODIFY_OWNER);
        }
        if (target.role() == newRole) {
            return MemberSummary.from(target);
        }
        Instant now = clock.instant();
        if (!repository.updateMembershipRole(tenantId, targetSubjectId, newRole.name(), now)) {
            throw new BusinessException(IdentityErrorCode.MEMBER_STATE_CONFLICT);
        }
        audit(
                tenantId, callerSubjectId, targetSubjectId, "ROLE_CHANGED", newRole.name(),
                reasonCode, requestId, now);
        return activeMember(tenantId, targetSubjectId);
    }

    @Transactional
    public void removeMember(
            AuthenticatedActor actor,
            UUID tenantId,
            UUID targetSubjectId,
            String reasonCode,
            String requestId) {
        UUID callerSubjectId = requireCallerManager(actor, tenantId, WRITE_AUTHORITY, true);
        requireAuditReference(reasonCode);
        requireAuditReference(requestId);
        IdentityDirectoryEntry target = repository.findActiveDirectoryEntryForUpdate(tenantId, targetSubjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.MEMBERSHIP_NOT_FOUND));
        if (target.role() == TenantRole.OWNER) {
            throw new BusinessException(IdentityErrorCode.CANNOT_MODIFY_OWNER);
        }
        Instant now = clock.instant();
        if (!repository.updateMembershipStatus(
                tenantId, targetSubjectId, IdentityStatus.ACTIVE, IdentityStatus.DISABLED, now)) {
            throw new BusinessException(IdentityErrorCode.MEMBER_STATE_CONFLICT);
        }
        audit(
                tenantId, callerSubjectId, targetSubjectId, "REMOVED", target.role().name(),
                reasonCode, requestId, now);
    }

    private UUID requireCallerManager(
            AuthenticatedActor actor,
            UUID tenantId,
            String authority,
            boolean lock) {
        if (actor == null || !actor.isUser() || !actor.hasAuthority(authority)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        UUID actorTenantId = trustedUuid(actor.tenantId());
        UUID actorSubjectId = trustedUuid(actor.subjectId());
        if (!tenantId.equals(actorTenantId)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        IdentityDirectoryEntry caller = (lock
                ? repository.findActiveDirectoryEntryForUpdate(tenantId, actorSubjectId)
                : repository.findActiveDirectoryEntry(tenantId, actorSubjectId))
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        if (caller.role() != TenantRole.ADMIN && caller.role() != TenantRole.OWNER) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return actorSubjectId;
    }

    private IdentityAccount resolveTarget(AddTenantMemberCommand command) {
        if (command == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
        boolean hasUsername = command.username() != null && !command.username().isBlank();
        boolean hasSubjectId = command.subjectId() != null;
        if (hasUsername == hasSubjectId) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
        return (hasUsername
                ? repository.findAccountByUsername(normalize(command.username()))
                : repository.findAccountBySubjectId(command.subjectId()))
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }

    private MemberSummary activeMember(UUID tenantId, UUID subjectId) {
        return repository.findActiveDirectoryEntry(tenantId, subjectId)
                .map(MemberSummary::from)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.MEMBER_STATE_CONFLICT));
    }

    private static void requireRoleAssignable(TenantRole role) {
        if (role == null || role == TenantRole.OWNER) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
    }

    private void audit(
            UUID tenantId,
            UUID actorSubjectId,
            UUID targetSubjectId,
            String operation,
            String role,
            String reasonCode,
            String requestId,
            Instant occurredAt) {
        repository.insertMemberAudit(
                tenantId, actorSubjectId, targetSubjectId, operation, role,
                reasonCode, requestId, occurredAt);
    }

    private static void requireAuditReference(String value) {
        if (value == null || !SAFE_AUDIT_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(IdentityErrorCode.INVALID_MEMBER_REQUEST);
        }
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID trustedUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
