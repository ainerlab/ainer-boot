package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for {@code ainer_authorization_change_audit} (ADR-0030 §11.7).
 * Append-only — insert is the sole write operation.
 */
@Mapper
public interface AuthorizationChangeAuditMapper {

    /**
     * Insert a change-audit row. The {@code id} column uses DB {@code DEFAULT uuidv7()} and is not
     * supplied by the caller. Returns affected row count (must be 1).
     */
    int insert(@Param("row") AuthorizationChangeAuditRow row);
}
