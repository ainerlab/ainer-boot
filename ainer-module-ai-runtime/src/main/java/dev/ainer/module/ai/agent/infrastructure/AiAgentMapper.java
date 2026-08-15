package dev.ainer.module.ai.agent.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AiAgentMapper {

    int insert(AiAgentRow row);

    AiAgentRow selectById(@Param("id") UUID id);

    int retire(@Param("id") UUID id, @Param("at") Instant at);

    List<AiAgentRow> page(@Param("offset") long offset, @Param("limit") int limit);
}
