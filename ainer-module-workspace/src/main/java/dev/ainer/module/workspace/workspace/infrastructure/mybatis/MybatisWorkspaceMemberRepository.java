package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspaceMemberRepository;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisWorkspaceMemberRepository implements WorkspaceMemberRepository {

    private final WorkspaceMemberMapper mapper;

    public MybatisWorkspaceMemberRepository(WorkspaceMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(WorkspaceMember member) {
        try {
            if (mapper.insert(toRow(member)) != 1) {
                throw new IllegalStateException("Workspace member insert affected an unexpected number of rows");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<WorkspaceMember> findByWorkspaceAndSubject(
            UUID workspaceId, SubjectId subjectId) {
        return Optional.ofNullable(mapper.selectByWorkspaceAndSubject(
                        workspaceId, subjectId.value()))
                .map(this::toDomain);
    }

    @Override
    public boolean activatePending(
            UUID workspaceId, SubjectId subjectId, Instant activatedAt) {
        return mapper.activatePending(
                workspaceId, subjectId.value(), activatedAt) == 1;
    }

    @Override
    public boolean updateRole(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            WorkspaceRole newRole,
            Instant updatedAt) {
        return mapper.updateRole(
                workspaceId, subjectId.value(), expectedRole, newRole, updatedAt) == 1;
    }

    @Override
    public boolean deleteNonOwner(UUID workspaceId, SubjectId subjectId) {
        return mapper.deleteNonOwner(workspaceId, subjectId.value()) == 1;
    }

    @Override
    public boolean demoteOwner(
            UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt) {
        return mapper.demoteOwner(
                workspaceId, ownerSubjectId.value(), updatedAt) == 1;
    }

    @Override
    public boolean promoteActiveMemberToOwner(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            Instant updatedAt) {
        return mapper.promoteActiveMemberToOwner(
                workspaceId, subjectId.value(), expectedRole, updatedAt) == 1;
    }

    @Override
    public boolean hasActiveOwner(UUID workspaceId) {
        return mapper.hasActiveOwner(workspaceId);
    }

    @Override
    public boolean hasRevokedOwner(UUID workspaceId) {
        return mapper.hasRevokedOwner(workspaceId);
    }

    private WorkspaceMemberRow toRow(WorkspaceMember member) {
        WorkspaceMemberRow row = new WorkspaceMemberRow();
        row.setWorkspaceId(member.workspaceId());
        row.setSubjectId(member.subjectId().value());
        row.setRole(member.role().name());
        row.setStatus(member.status().name());
        row.setInvitedBy(member.invitedBy().value());
        row.setCreatedAt(member.createdAt());
        row.setActivatedAt(member.activatedAt());
        row.setUpdatedAt(member.updatedAt());
        return row;
    }

    private WorkspaceMember toDomain(WorkspaceMemberRow row) {
        return new WorkspaceMember(
                row.getWorkspaceId(),
                new SubjectId(row.getSubjectId()),
                WorkspaceRole.valueOf(row.getRole()),
                WorkspaceMemberStatus.valueOf(row.getStatus()),
                new SubjectId(row.getInvitedBy()),
                row.getCreatedAt(),
                row.getActivatedAt(),
                row.getUpdatedAt());
    }
}
