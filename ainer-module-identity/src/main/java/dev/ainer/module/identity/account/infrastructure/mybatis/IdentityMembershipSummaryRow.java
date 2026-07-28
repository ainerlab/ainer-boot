package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.util.UUID;

public class IdentityMembershipSummaryRow {

    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private String role;
    private boolean defaultTenant;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(boolean defaultTenant) {
        this.defaultTenant = defaultTenant;
    }
}
