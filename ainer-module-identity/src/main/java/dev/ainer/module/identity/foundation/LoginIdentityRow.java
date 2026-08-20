package dev.ainer.module.identity.foundation;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link LoginIdentity} 的可变持久化行（MyBatis 普通 POJO 映射，对应表
 * {@code ainer_identity_login_identity}）。{@code lastUsedAt} 可为 null。
 */
public class LoginIdentityRow {

    private UUID id;
    private UUID accountId;
    private String type;
    private String providerAuthority;
    private String normalizedIdentifier;
    private String status;
    private Instant verifiedAt;
    private Instant linkedAt;
    private Instant lastUsedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProviderAuthority() {
        return providerAuthority;
    }

    public void setProviderAuthority(String providerAuthority) {
        this.providerAuthority = providerAuthority;
    }

    public String getNormalizedIdentifier() {
        return normalizedIdentifier;
    }

    public void setNormalizedIdentifier(String normalizedIdentifier) {
        this.normalizedIdentifier = normalizedIdentifier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
