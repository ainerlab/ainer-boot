package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.policy.DelayedSelfElevationDetector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用变更审计 actor 与仍 ACTIVE 的岗位集合绑定做交叉查询（ADR-0050）。
 */
@Component
public class JdbcDelayedSelfElevationDetector implements DelayedSelfElevationDetector {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDelayedSelfElevationDetector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findSelfCreatedPositionAssigneeBinding(
            String subjectIssuer, String subjectId, UUID workspaceId, UUID positionId) {
        List<UUID> ids = jdbcTemplate.query("""
                SELECT b.id
                FROM ainer_authorization_subject_set_binding b
                INNER JOIN ainer_authorization_change_audit a
                    ON a.target_id = b.id
                   AND a.target_type = 'SET_BINDING'
                   AND a.action = 'CREATE'
                WHERE b.set_object_type = 'workforce.position'
                  AND b.set_relation = 'assignee'
                  AND b.set_object_id = ?
                  AND b.set_workspace_id = ?
                  AND b.status = 'ACTIVE'
                  AND a.actor_issuer = ?
                  AND a.actor_id = ?
                ORDER BY b.id
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                positionId, workspaceId, subjectIssuer, subjectId);
        return ids.stream().findFirst();
    }
}
