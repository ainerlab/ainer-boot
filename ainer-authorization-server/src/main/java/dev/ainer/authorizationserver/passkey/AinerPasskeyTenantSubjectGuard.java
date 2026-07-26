package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * 将 Passkey 安全控制面中的 tenant 与 Identity 主体权威关系重新绑定。
 *
 * <p>Passkey credential 属于全局 Identity 主体，因此 tenant-bound 操作员只能管理以目标 tenant
 * 作为 ACTIVE 默认租户的 ACTIVE 主体。仅校验路径 tenant 或 subject 外键不足以阻止跨租户操作。
 */
final class AinerPasskeyTenantSubjectGuard {

    private final JdbcTemplate jdbcTemplate;

    AinerPasskeyTenantSubjectGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void requireActiveHomeTenantSubject(UUID tenantId, UUID subjectId) {
        Assert.notNull(tenantId, "tenantId cannot be null");
        Assert.notNull(subjectId, "subjectId cannot be null");
        Integer matches = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_identity_membership membership
                JOIN ainer_identity_user identity_user
                  ON identity_user.id = membership.user_id
                JOIN ainer_identity_tenant tenant
                  ON tenant.id = membership.tenant_id
                WHERE membership.tenant_id = ?
                  AND membership.user_id = ?
                  AND membership.is_default = true
                  AND membership.status = 'ACTIVE'
                  AND identity_user.status = 'ACTIVE'
                  AND tenant.status = 'ACTIVE'
                """,
                Integer.class,
                tenantId,
                subjectId);
        if (matches == null || matches != 1) {
            throw new BusinessException(PasskeyErrorCode.TENANT_SUBJECT_NOT_FOUND);
        }
    }
}
