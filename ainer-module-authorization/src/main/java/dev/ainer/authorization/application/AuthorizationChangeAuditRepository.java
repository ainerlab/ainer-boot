package dev.ainer.authorization.application;

/**
 * Persistence port for {@link AuthorizationChangeAudit} (ADR-0030 §11.7). Append-only — no update
 * or delete. Implemented by the infrastructure layer; consumed by
 * {@link AuthorizationChangeAuditService}.
 */
public interface AuthorizationChangeAuditRepository {

    /**
     * Insert a single audit row. Throws on failure so the caller's transaction rolls back.
     */
    void insert(AuthorizationChangeAudit audit);
}
