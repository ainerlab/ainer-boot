-- M4.8C 安全不变量：每个 tenant 最多一个 ACTIVE OWNER。
-- 先检查历史数据是否存在重复 ACTIVE OWNER，存在则阻止升级。
DO $$
DECLARE
    duplicate_count integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT tenant_id
        FROM ainer_identity_membership
        WHERE role = 'OWNER' AND status = 'ACTIVE'
        GROUP BY tenant_id
        HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'Found % tenant(s) with multiple ACTIVE OWNER memberships; '
            'cannot create unique index. Resolve duplicate OWNERs before upgrading.',
            duplicate_count;
    END IF;
END $$;

CREATE UNIQUE INDEX ux_ainer_identity_membership_active_owner
    ON ainer_identity_membership (tenant_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';
