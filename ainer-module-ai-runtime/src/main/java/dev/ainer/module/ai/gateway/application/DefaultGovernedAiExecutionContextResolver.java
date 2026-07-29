package dev.ainer.module.ai.gateway.application;

import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 默认实现：从 {@link AuthenticatedActor} 解析当前已有的身份字段。
 *
 * <p>tenantId, actorType, actorId, scopes 来自 JWT；workspaceId, memberId,
 * identityId 等字段待领域模型对接后由扩展实现填充。
 */
@Component
public class DefaultGovernedAiExecutionContextResolver
        implements GovernedAiExecutionContextResolver {

    @Override
    public GovernedAiExecutionContext resolve(AuthenticatedActor actor, String requestId) {
        return new GovernedAiExecutionContext(
                UUID.fromString(actor.tenantId()),
                null,
                actor.actorType(),
                actor.subjectId(),
                null,
                null,
                null,
                null,
                null,
                actor.authorities(),
                null,
                null,
                null,
                null,
                null,
                requestId);
    }
}
