package dev.ainer.module.ai.agent.application;

import dev.ainer.authorization.policy.AgentDefinitionStatusResolver;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.ai.agent.domain.AiAgentDefinition;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent 定义注册与退役（ADR-0043 A1）。code+version 唯一；退役后委托检查点立即拒绝。
 * 同时向授权模块暴露 {@link AgentDefinitionStatusResolver}（同一 bean，决策时实时读取）。
 */
@Service
public class AiAgentApplicationService implements AgentDefinitionStatusResolver {

    public static final String SCOPE_MANAGE = "ai.agents.manage";

    private final AiAgentRepository repository;
    private final Clock clock;

    public AiAgentApplicationService(AiAgentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public AgentStatus agentStatus(UUID agentId) {
        return repository.findById(agentId)
                .map(agent -> agent.active() ? AgentStatus.ACTIVE : AgentStatus.RETIRED)
                .orElse(AgentStatus.UNKNOWN);
    }

    @Transactional
    public AiAgentDefinition register(
            AuthenticatedPrincipal principal, String code, String version, String purpose,
            @Nullable String runtimeRef, @Nullable UUID workspaceId) {
        requireManage(principal);
        if (code == null || code.isBlank() || version == null || version.isBlank()
                || purpose == null || purpose.isBlank()) {
            throw new BusinessException(AiAgentErrorCode.INVALID_DEFINITION);
        }
        Instant now = clock.instant();
        AiAgentDefinition agent = new AiAgentDefinition(Uuidv7.generate(), code.strip(),
                version.strip(), "ACTIVE", purpose.strip(), runtimeRef, workspaceId, now, now, null);
        try {
            repository.insert(agent);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(AiAgentErrorCode.CODE_VERSION_CONFLICT);
        }
        return agent;
    }

    @Transactional
    public AiAgentDefinition retire(AuthenticatedPrincipal principal, UUID agentId) {
        requireManage(principal);
        AiAgentDefinition agent = repository.findById(agentId)
                .orElseThrow(() -> new BusinessException(AiAgentErrorCode.AGENT_NOT_FOUND));
        if (!agent.active()) {
            throw new BusinessException(AiAgentErrorCode.ALREADY_RETIRED);
        }
        Instant now = clock.instant();
        repository.retire(agentId, now);
        return repository.findById(agentId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public AiAgentDefinition get(AuthenticatedPrincipal principal, UUID agentId) {
        requireManage(principal);
        return repository.findById(agentId)
                .orElseThrow(() -> new BusinessException(AiAgentErrorCode.AGENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<AiAgentDefinition> page(AuthenticatedPrincipal principal, long page, long size) {
        requireManage(principal);
        long safePage = Math.max(page, 1);
        int safeSize = (int) Math.min(Math.max(size, 1), 100);
        return repository.page((safePage - 1) * safeSize, safeSize);
    }

    @Transactional(readOnly = true)
    public long count(AuthenticatedPrincipal principal) {
        requireManage(principal);
        return repository.count();
    }

    private static void requireManage(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(SCOPE_MANAGE)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
