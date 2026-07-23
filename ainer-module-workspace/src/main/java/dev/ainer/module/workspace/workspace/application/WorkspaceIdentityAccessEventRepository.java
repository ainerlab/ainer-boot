package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.UUID;

public interface WorkspaceIdentityAccessEventRepository {

    boolean insertReceipt(WorkspaceIdentityAccessEvent event, Instant receivedAt);

    int revokeExistingMemberships(WorkspaceIdentityAccessEvent event, Instant receivedAt);

    void recordAffectedMemberships(UUID eventId, int affectedMemberships);

    int findAffectedMemberships(UUID eventId);
}
