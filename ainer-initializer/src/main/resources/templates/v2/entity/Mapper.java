package {{package.name}}.{{entity.package}}.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 显式参数绑定 SQL；每条资源语句都包含 workspace_id。 */
@Mapper
public interface {{entity.className}}Mapper {

    @Select("""
            INSERT INTO {{table.name}} (
                workspace_id{{entity.insertColumns}}, version,
                created_by_subject_id, updated_by_subject_id, created_at, updated_at
            ) VALUES (
                #{row.workspaceId}{{entity.insertParams}}, 0,
                #{row.createdBySubjectId}, #{row.updatedBySubjectId}, #{row.createdAt}, #{row.updatedAt}
            ) RETURNING id
            """)
    UUID insertReturningId(@Param("row") {{entity.className}}Row row);

    @Select("""
            SELECT id, workspace_id{{entity.selectColumns}}, version,
                   created_by_subject_id, updated_by_subject_id, created_at, updated_at
              FROM {{table.name}}
             WHERE workspace_id = #{workspaceId}
               AND id = #{id}
            """)
    {{entity.className}}Row findByWorkspaceAndId(
            @Param("workspaceId") UUID workspaceId,
            @Param("id") UUID id);

    @Select("""
            SELECT id, workspace_id{{entity.selectColumns}}, version,
                   created_by_subject_id, updated_by_subject_id, created_at, updated_at
              FROM {{table.name}}
             WHERE workspace_id = #{workspaceId}
             ORDER BY updated_at DESC, id DESC
             LIMIT #{size} OFFSET #{offset}
            """)
    List<{{entity.className}}Row> findPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("size") int size,
            @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM {{table.name}} WHERE workspace_id = #{workspaceId}")
    long countByWorkspace(@Param("workspaceId") UUID workspaceId);

    @Update("""
            UPDATE {{table.name}}
               SET
{{entity.updateAssignments}}            version = version + 1,
                   updated_by_subject_id = #{row.updatedBySubjectId},
                   updated_at = #{row.updatedAt}
             WHERE workspace_id = #{workspaceId}
               AND id = #{id}
               AND version = #{expectedVersion}
            """)
    int updateByWorkspaceAndVersion(
            @Param("workspaceId") UUID workspaceId,
            @Param("id") UUID id,
            @Param("expectedVersion") long expectedVersion,
            @Param("row") {{entity.className}}Row row);

    @Delete("""
            DELETE FROM {{table.name}}
             WHERE workspace_id = #{workspaceId}
               AND id = #{id}
               AND version = #{expectedVersion}
            """)
    int deleteByWorkspaceAndVersion(
            @Param("workspaceId") UUID workspaceId,
            @Param("id") UUID id,
            @Param("expectedVersion") long expectedVersion);

    @Insert("""
            INSERT INTO {{audit.table.name}} (
                id, workspace_id, resource_id, actor_subject_id, action,
                decision, reason_code, request_id, occurred_at
            ) VALUES (
                #{auditId}, #{workspaceId}, #{resourceId}, #{actorSubjectId}, #{action},
                #{decision}, #{reasonCode}, #{requestId}, #{occurredAt}
            )
            """)
    int insertAccessAudit(
            @Param("auditId") UUID auditId,
            @Param("workspaceId") UUID workspaceId,
            @Param("resourceId") UUID resourceId,
            @Param("actorSubjectId") String actorSubjectId,
            @Param("action") String action,
            @Param("decision") String decision,
            @Param("reasonCode") String reasonCode,
            @Param("requestId") String requestId,
            @Param("occurredAt") Instant occurredAt);
}
