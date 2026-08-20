package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * 为给定资源提供原始授权事实（所有者、参与者、资源状态）（ADR-0030 §5.1）。由产品/
 * 领域模块实现该端口；{@link DomainAuthorizationPolicy} 消费这些事实来求值关系派生授权
 * 与资源状态条件。求值器绝不直接调用该端口——一律经由策略。
 */
@FunctionalInterface
public interface AuthorizationFactsProvider {

    AuthorizationFacts factsFor(Requester.Authenticated subject, ResourceRef resource, AuthorizationContext context);

    /** 最小类型化事实；缺失值为 {@code null} 或空集，绝不捏造。 */
    record AuthorizationFacts(
            @Nullable UUID ownerSubjectId,
            Set<UUID> participantSubjectIds,
            @Nullable String resourceState) {

        public AuthorizationFacts {
            participantSubjectIds = participantSubjectIds != null ? Set.copyOf(participantSubjectIds) : Set.of();
        }
    }
}
