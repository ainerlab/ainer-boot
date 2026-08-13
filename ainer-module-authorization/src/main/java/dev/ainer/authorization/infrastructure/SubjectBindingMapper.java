package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper for the subject binding aggregate (ADR-0030 S1).
 */
@Mapper
public interface SubjectBindingMapper {

    /**
     * Insert a binding row and return the database-generated UUIDv7 primary key.
     * Uses {@code INSERT ... RETURNING id} so must be a {@code <select>} (MyBatis disallows non-int
     * return types on {@code <insert>}).
     */
    UUID insertReturningId(@Param("row") SubjectBindingRow row, @Param("now") Instant now);

    SubjectBindingRow selectById(@Param("id") UUID id);

    /**
     * Return all ACTIVE bindings for the given subject whose validity window contains {@code at}.
     * Revoked or expired rows are excluded at the database level — there is no ALLOW cache.
     */
    List<SubjectBindingRow> selectLiveBindings(
            @Param("issuer") String issuer,
            @Param("subjectType") String subjectType,
            @Param("subjectId") String subjectId,
            @Param("at") Instant at);

    List<SubjectBindingRow> selectAllBySubject(
            @Param("issuer") String issuer,
            @Param("subjectType") String subjectType,
            @Param("subjectId") String subjectId);

    /**
     * Logically revoke a binding: set status=REVOKED, record revocation metadata, bump version.
     * Returns the number of affected rows (0 if not found or already revoked).
     */
    int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt,
               @Param("reason") String reason, @Param("now") Instant now);
}
