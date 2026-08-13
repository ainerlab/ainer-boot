package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the permission catalog projection (ADR-0030 S1).
 */
@Mapper
public interface PermissionMapper {

    /**
     * Upsert a permission definition. Uses ON CONFLICT(code) to handle idempotent re-registration.
     * The application layer detects definition conflicts at startup via PermissionRegistry.
     */
    int upsert(@Param("row") PermissionRow row, @Param("now") java.time.Instant now);

    List<PermissionRow> selectAll();
}
