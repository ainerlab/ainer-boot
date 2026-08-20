package dev.ainer.authorization.application;

/**
 * {@link AuthorizationChangeAudit} 的持久化端口（ADR-0030 §11.7）。append-only——
 * 没有 update 或 delete。由基础设施层实现；由 {@link AuthorizationChangeAuditService} 消费。
 */
public interface AuthorizationChangeAuditRepository {

    /**
     * 插入单条审计行。失败时抛出异常，使调用方事务回滚。
     */
    void insert(AuthorizationChangeAudit audit);
}
