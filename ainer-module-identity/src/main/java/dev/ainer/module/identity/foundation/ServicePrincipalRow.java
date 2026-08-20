package dev.ainer.module.identity.foundation;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link ServicePrincipal} 的可变持久化行（MyBatis 普通 POJO 映射，对应表
 * {@code ainer_identity_service_principal}）。受 ORM 约束使用普通可变类而非 record。
 */
public class ServicePrincipalRow {

    private UUID id;
    private String issuer;
    private String realm;
    private String status;
    private long securityEpoch;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getSecurityEpoch() {
        return securityEpoch;
    }

    public void setSecurityEpoch(long securityEpoch) {
        this.securityEpoch = securityEpoch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
