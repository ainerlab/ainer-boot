package dev.ainer.authorization.application;

/**
 * Persistence port for {@link AuthorizationDecisionAudit} (ADR-0030 §12.4). Append-only.
 */
public interface AuthorizationDecisionAuditRepository {

    /**
     * Insert a single decision-audit row. Throws on failure.
     */
    void insert(AuthorizationDecisionAudit audit);
}
