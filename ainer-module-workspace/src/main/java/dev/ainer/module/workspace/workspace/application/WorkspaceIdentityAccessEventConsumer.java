package dev.ainer.module.workspace.workspace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class WorkspaceIdentityAccessEventConsumer {

    private final WorkspaceIdentityAccessEventRepository repository;
    private final Clock clock;

    public WorkspaceIdentityAccessEventConsumer(
            WorkspaceIdentityAccessEventRepository repository,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceIdentityAccessEventResult consume(WorkspaceIdentityAccessEvent event) {
        Objects.requireNonNull(event, "event");
        Instant receivedAt = clock.instant();
        if (!repository.insertReceipt(event, receivedAt)) {
            return new WorkspaceIdentityAccessEventResult(
                    true, repository.findAffectedMemberships(event.eventId()));
        }

        // Account disable/revocation is checked by the current Identity/Workspace authorization facts.
        // A subject-only event must not revoke memberships across every Workspace.
        int affectedMemberships = 0;
        repository.recordAffectedMemberships(event.eventId(), affectedMemberships);
        return new WorkspaceIdentityAccessEventResult(false, affectedMemberships);
    }
}
