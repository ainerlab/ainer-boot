package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 主体 Binding 聚合的 MyBatis mapper（ADR-0030 S1）。
 */
@Mapper
public interface SubjectBindingMapper {

    /**
     * 插入 Binding 行并返回数据库生成的 UUIDv7 主键。使用
     * {@code INSERT ... RETURNING id}，因此必须是 {@code <select>}（MyBatis 不允许
     * {@code <insert>} 返回非 int 类型）。
     */
    UUID insertReturningId(@Param("row") SubjectBindingRow row, @Param("now") Instant now);

    SubjectBindingRow selectById(@Param("id") UUID id);

    /**
     * 返回给定主体全部有效窗口覆盖 {@code at} 的 ACTIVE Binding。已撤销或已过期的行
     * 在数据库层就被排除——没有 ALLOW 缓存。
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
     * 逻辑撤销 Binding：置 status=REVOKED、记录撤销元数据、递增版本。返回受影响行数
     * （不存在或已被撤销时为 0）。
     */
    int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt,
               @Param("reason") String reason, @Param("now") Instant now);
}
