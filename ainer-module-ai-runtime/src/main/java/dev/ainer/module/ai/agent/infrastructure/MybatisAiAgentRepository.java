package dev.ainer.module.ai.agent.infrastructure;

import dev.ainer.module.ai.agent.application.AiAgentRepository;
import dev.ainer.module.ai.agent.domain.AiAgentDefinition;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisAiAgentRepository implements AiAgentRepository {

    private final AiAgentMapper mapper;

    public MybatisAiAgentRepository(AiAgentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AiAgentDefinition agent) {
        mapper.insert(toRow(agent));
    }

    @Override
    public Optional<AiAgentDefinition> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisAiAgentRepository::toDomain);
    }

    @Override
    public void retire(UUID id, Instant at) {
        mapper.retire(id, at);
    }

    @Override
    public List<AiAgentDefinition> page(long offset, int limit) {
        return mapper.page(offset, limit).stream().map(MybatisAiAgentRepository::toDomain).toList();
    }

    @Override
    public long count() {
        return mapper.countAll();
    }

    private static AiAgentRow toRow(AiAgentDefinition agent) {
        AiAgentRow row = new AiAgentRow();
        row.setId(agent.id());
        row.setCode(agent.code());
        row.setAgentVersion(agent.version());
        row.setStatus(agent.status());
        row.setPurpose(agent.purpose());
        row.setRuntimeRef(agent.runtimeRef());
        row.setWorkspaceId(agent.workspaceId());
        row.setCreatedAt(agent.createdAt());
        row.setUpdatedAt(agent.updatedAt());
        row.setRetiredAt(agent.retiredAt());
        return row;
    }

    private static AiAgentDefinition toDomain(AiAgentRow row) {
        return new AiAgentDefinition(row.getId(), row.getCode(), row.getAgentVersion(),
                row.getStatus(), row.getPurpose(), row.getRuntimeRef(), row.getWorkspaceId(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getRetiredAt());
    }
}
