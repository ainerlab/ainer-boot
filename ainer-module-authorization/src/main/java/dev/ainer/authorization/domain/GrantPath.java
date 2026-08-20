package dev.ainer.authorization.domain;

/**
 * 由 {@code DomainAuthorizationPolicy} 为已认证动作声明的授权路径（ADR-0030 §1、§6.1）。
 * {@code PUBLIC} 由 {@code PublicAccessPolicy} 单独处理，不在此列。
 */
public enum GrantPath {
    /** 要求完整的所有者/参与者关系加已认证约束；不需要 SubjectBinding。 */
    RELATION_DERIVED,
    /** 要求 live 且 scope 匹配的 SubjectBinding，加上策略声明的关系/状态。 */
    BINDING_REQUIRED,
    /** 完整的 Binding 分支或完整的关系分支，再与状态/风险求交集。 */
    BINDING_OR_RELATION
}
