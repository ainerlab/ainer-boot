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
 * 默认的 {@link SubjectSetMembershipRegistry}：聚合所有产品提供的
 * {@link SubjectSetMembershipResolver}。没有提供者的集合族不受支持，
 * 每次成员关系求值都 fail-closed（返回 {@code UNAVAILABLE}）。
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
