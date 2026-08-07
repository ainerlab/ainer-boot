package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryRequest;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceOwnerRecoveryResponse(
        UUID requestId,
        UUID workspaceId,
        String newOwnerSubjectId,
        String requestedBy,
        String approvedBy,
        String incidentReference,
        String status,
        Instant requestedAt,
        Instant expiresAt,
        Instant executedAt) {

    static WorkspaceOwnerRecoveryResponse from(WorkspaceOwnerRecoveryRequest request) {
        return new WorkspaceOwnerRecoveryResponse(
                request.id(), request.workspaceId(),
                request.newOwnerSubjectId().value(), request.requestedBy(), request.approvedBy(),
                request.incidentReference(), request.status(), request.requestedAt(),
                request.expiresAt(), request.executedAt());
    }
}
