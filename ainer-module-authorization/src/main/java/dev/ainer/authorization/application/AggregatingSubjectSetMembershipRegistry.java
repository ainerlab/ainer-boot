package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;
import dev.ainer.authorization.policy.SubjectSetMembership;
import dev.ainer.authorization.policy.SubjectSetMembershipRegistry;
import dev.ainer.authorization.policy.SubjectSetMembershipResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Default {@link SubjectSetMembershipRegistry}: aggregates every product-provided
 * {@link SubjectSetMembershipResolver}. Families without a provider are unsupported and every
 * membership evaluation is fail-closed ({@code UNAVAILABLE}).
 */
@Component
public class AggregatingSubjectSetMembershipRegistry implements SubjectSetMembershipRegistry {

    private final ObjectProvider<SubjectSetMembershipResolver> resolvers;

    public AggregatingSubjectSetMembershipRegistry(ObjectProvider<SubjectSetMembershipResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public boolean supports(SubjectSetRef set) {
        for (SubjectSetMembershipResolver resolver : resolvers.orderedStream().toList()) {
            if (resolver.supports(set.objectType(), set.relation())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SubjectSetMembership membership(SubjectRef requester, SubjectSetRef set, Instant evaluationTime) {
        for (SubjectSetMembershipResolver resolver : resolvers.orderedStream().toList()) {
            if (resolver.supports(set.objectType(), set.relation())) {
                try {
                    return resolver.resolve(requester, set, evaluationTime);
                } catch (RuntimeException unexpected) {
                    return SubjectSetMembership.unavailable();
                }
            }
        }
        return SubjectSetMembership.unavailable();
    }
}
