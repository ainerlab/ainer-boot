package dev.ainer.module.ai.gateway.application;

import dev.ainer.authorization.application.ActingGrantApplicationService;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 网关代行检查点：仅当请求带 {@code actingAgentId} 时调用 {@code ActingGrant.check}。
 * 人员直调跳过。授权模块未装配或检查拒绝一律 fail-closed。
 */
@Component
public class AiGatewayActingGrantGuard {

    static final PermissionCode INVOKE = new PermissionCode("ai.invoke");
    static final ResourceType REQUEST_RESOURCE = new ResourceType("request");

    private final ObjectProvider<ActingGrantApplicationService> actingGrants;

    public AiGatewayActingGrantGuard(ObjectProvider<ActingGrantApplicationService> actingGrants) {
        this.actingGrants = actingGrants;
    }

    public void requireIfPresent(
            AuthenticatedPrincipal principal,
            UUID actingAgentId,
            UUID workspaceId,
            String requestUri) {
        if (actingAgentId == null) {
            return;
        }
        if (workspaceId == null || requestUri == null || requestUri.isBlank()) {
            throw new BusinessException(AiGatewayErrorCode.INVALID_ACTING_CONTEXT);
        }
        ActingGrantApplicationService service = actingGrants.getIfAvailable();
        if (service == null) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        SubjectRef subject = new SubjectRef(
                principal.authority().issuer(),
                principal.subjectId(),
                principal.isService() ? SubjectType.SERVICE : SubjectType.USER);
        ResourceRef resource = new ResourceRef(
                workspaceId,
                REQUEST_RESOURCE,
                UUID.nameUUIDFromBytes(requestUri.getBytes(StandardCharsets.UTF_8)));
        ActingGrantApplicationService.DelegationCheck check =
                service.check(subject, actingAgentId, INVOKE, resource, "ai-gateway");
        if (!check.allowed()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
