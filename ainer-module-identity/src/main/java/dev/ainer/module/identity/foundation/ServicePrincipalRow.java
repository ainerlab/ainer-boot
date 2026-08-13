package dev.ainer.module.identity.foundation;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable persistence row for {@link ServicePrincipal} (MyBatis plain-POJO mapping against
 * {@code ainer_identity_service_principal}). ORM constraint: a normal mutable class, not a record.
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
