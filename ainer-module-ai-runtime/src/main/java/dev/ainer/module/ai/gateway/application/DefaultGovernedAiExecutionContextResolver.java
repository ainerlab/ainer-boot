package dev.ainer.module.ai.gateway.application;

import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 默认实现：从 typed {@link AuthenticatedPrincipal} 解析当前身份字段。
 *
 * <p>actorType, actorId, scopes 来自 JWT；workspaceId, memberId,
 * identityId 等字段待领域模型对接后由扩展实现填充。
 */
@Component
public class DefaultGovernedAiExecutionContextResolver
        implements GovernedAiExecutionContextResolver {

    @Override
    public GovernedAiExecutionContext resolve(AuthenticatedPrincipal principal, String requestId) {
        return new GovernedAiExecutionContext(
                null,
                principal.isHuman() ? "USER" : "SERVICE",
                principal.subjectId(),
                null,
                null,
                null,
                null,
                null,
                principal.scopes(),
                null,
                null,
                null,
                null,
                null,
                requestId);
    }
}
