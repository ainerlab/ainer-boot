package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Provides raw authorization facts (owner, participants, resource state) for a given resource
 * (ADR-0030 §5.1). Product/domain modules implement this port; the {@link DomainAuthorizationPolicy}
 * consumes these facts to evaluate relation-derived grants and resource-state conditions. The evaluator
 * never calls this port directly — it goes through the policy.
 */
@FunctionalInterface
public interface AuthorizationFactsProvider {

    AuthorizationFacts factsFor(Requester.Authenticated subject, ResourceRef resource, AuthorizationContext context);

    /** Minimal typed facts; absent values are {@code null} or empty, never fabricated. */
    record AuthorizationFacts(
            @Nullable UUID ownerSubjectId,
            Set<UUID> participantSubjectIds,
            @Nullable String resourceState) {

        public AuthorizationFacts {
            participantSubjectIds = participantSubjectIds != null ? Set.copyOf(participantSubjectIds) : Set.of();
        }
    }
}
