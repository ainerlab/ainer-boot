package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;

public class IdentityTokenStatusRow {

    private String tenantStatus;
    private String userStatus;
    private String membershipStatus;
    private Instant latestRevokedAt;

    public String getTenantStatus() {
        return tenantStatus;
    }

    public void setTenantStatus(String tenantStatus) {
        this.tenantStatus = tenantStatus;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public String getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(String membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public Instant getLatestRevokedAt() {
        return latestRevokedAt;
    }

    public void setLatestRevokedAt(Instant latestRevokedAt) {
        this.latestRevokedAt = latestRevokedAt;
    }
}
