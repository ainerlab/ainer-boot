package dev.ainer.authorization.application;

/**
 * {@link AuthorizationDecisionAudit} 的持久化端口（ADR-0030 §12.4）。append-only。
 */
public interface AuthorizationDecisionAuditRepository {

    /**
     * 插入单条决策审计行。失败时抛出异常。
     */
    void insert(AuthorizationDecisionAudit audit);
}
