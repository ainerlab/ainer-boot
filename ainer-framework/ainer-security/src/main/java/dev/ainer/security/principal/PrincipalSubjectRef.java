package dev.ainer.security.principal;

/**
 * 带权威限定的凭证主体引用（ADR-0033 Greenfield §2.6、ADR-0030 §2.2）。
 *
 * <p>只有 Human 与 Service 是凭证主体。该类型为 sealed，使授权绑定、JWT 解析器、
 * 凭证/有效主体 API 与 {@code SubjectBinding} 目标可以穷尽地接受
 * {@code PrincipalSubjectRef}，绝不会静默放入 Agent 或匿名值。
 *
 * <p>不等价性是结构性的：即使原始 ID 恰好相同，Human 与 Service 也绝不是同一主体；
 * 同一 ID 处于不同 {@link IdentityAuthorityRef 权威} 下时也绝不相等。
 */
public sealed interface PrincipalSubjectRef
        permits HumanSubjectRef, ServiceSubjectRef {

    /**
     * 限定本主体 ID 的权威。
     */
    IdentityAuthorityRef authority();

    /**
     * 主体在其权威内的稳定标识（Human 为账号 ID，Service 为 ServicePrincipal ID）。
     * 归因与审计的统一访问入口；绝不是原始 JWT claim 集。
     */
    String subjectId();
}
