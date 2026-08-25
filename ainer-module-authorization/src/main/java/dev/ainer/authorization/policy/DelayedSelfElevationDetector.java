package dev.ainer.authorization.policy;

import java.util.Optional;
import java.util.UUID;

/**
 * 延迟自提权探测端口（ADR-0050）：任职 subject 是否曾创建仍有效的
 * {@code workforce.position#assignee} 集合绑定。组织模块只依赖本接口。
 */
public interface DelayedSelfElevationDetector {

    Optional<UUID> findSelfCreatedPositionAssigneeBinding(
            String subjectIssuer, String subjectId, UUID workspaceId, UUID positionId);
}
