package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEvent;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class MybatisWorkspaceIdentityAccessEventRepository
        implements WorkspaceIdentityAccessEventRepository {

    private final WorkspaceIdentityAccessEventMapper mapper;

    public MybatisWorkspaceIdentityAccessEventRepository(WorkspaceIdentityAccessEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean insertReceipt(WorkspaceIdentityAccessEvent event, Instant receivedAt) {
        return mapper.insertReceipt(
                event.eventId(),
                event.eventType(),
                event.subjectId(),
                event.payloadVersion(),
                event.occurredAt(),
                receivedAt) == 1;
    }

    @Override
    public void recordAffectedMemberships(UUID eventId, int affectedMemberships) {
        if (mapper.updateAffectedMemberships(eventId, affectedMemberships) != 1) {
            throw new IllegalStateException("Identity access event receipt disappeared during consumption");
        }
    }

    @Override
    public int findAffectedMemberships(UUID eventId) {
        Integer affectedMemberships = mapper.selectAffectedMemberships(eventId);
        if (affectedMemberships == null) {
            throw new IllegalStateException("Identity access event receipt was not found after conflict");
        }
        return affectedMemberships;
    }
}
