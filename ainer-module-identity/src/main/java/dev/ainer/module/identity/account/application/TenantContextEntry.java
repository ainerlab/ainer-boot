package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.TenantRole;

import java.util.Objects;
import java.util.UUID;

/**
 * 用户在某个 tenant 下的 ACTIVE membership 安全投影，用于租户上下文选择。
 *
 * <p>只包含选择所需的最小字段，不暴露密码哈希、OAuth 协议数据或 membership 内部状态。
 */
public record TenantContextEntry(
        UUID tenantId,
        String tenantCode,
        String tenantName,
        TenantRole role,
        boolean defaultTenant) {

    public TenantContextEntry {
        Objects.requireNonNull(tenantId, "tenantId");
        tenantCode = requireText(tenantCode, "tenantCode", 64);
        tenantName = requireText(tenantName, "tenantName", 200);
        Objects.requireNonNull(role, "role");
    }

    private static String requireText(String value, String name, int maxLength) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
