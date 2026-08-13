package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for {@code ainer_authorization_decision_audit} (ADR-0030 §12.4).
 * Append-only — insert is the sole write operation.
 */
@Mapper
public interface AuthorizationDecisionAuditMapper {

    /**
     * Insert a decision-audit row. The {@code decision_id} is the application-supplied decisionId
     * (UUIDv7) and is the primary key.
     */
    int insert(@Param("row") AuthorizationDecisionAuditRow row);
}
