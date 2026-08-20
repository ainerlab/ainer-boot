package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code ainer_authorization_decision_audit} 的 MyBatis mapper（ADR-0030 §12.4）。
 * append-only——insert 是唯一的写操作。
 */
@Mapper
public interface AuthorizationDecisionAuditMapper {

    /**
     * 插入一条决策审计行。{@code decision_id} 是应用侧提供的 decisionId（UUIDv7），
     * 即主键。
     */
    int insert(@Param("row") AuthorizationDecisionAuditRow row);
}
