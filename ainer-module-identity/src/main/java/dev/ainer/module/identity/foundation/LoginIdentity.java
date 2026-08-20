package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一个认证标识符到 {@link HumanAccount} 的受控绑定（ADR-0033 Greenfield §4）。
 *
 * <p>一个 HumanAccount 持有 1..n 个 LoginIdentity（聚合的无凭证不变量：一旦认证过，
 * 不存在没有任何已验证绑定的 HumanAccount）。{@code providerAuthority} + {@code type}
 * + {@code normalizedIdentifier} 是绑定的唯一键；同一原始标识符在不同 provider/realm
 * 下是不同绑定，绝不自动合并账号。凭证材料（密码哈希、WebAuthn 公钥、provider token）
 * 不存储在这里——存放在绑定所引用的专用凭证存储中。
 */
public record LoginIdentity(
        UUID identityId,
        UUID accountId,
        LoginIdentityType type,
        String providerAuthority,
        String normalizedIdentifier,
        LoginIdentityStatus status,
        Instant verifiedAt,
        Instant linkedAt,
        @Nullable Instant lastUsedAt) {

    public LoginIdentity {
        Objects.requireNonNull(identityId, "identityId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(providerAuthority, "providerAuthority");
        Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(linkedAt, "linkedAt");
        if (providerAuthority.isBlank() || normalizedIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "providerAuthority and normalizedIdentifier must be non-blank");
        }
    }

    /** 该绑定自身是否可用于认证（仅自身状态；账号 epoch 另行校验）。 */
    public boolean isActive() {
        return status == LoginIdentityStatus.ACTIVE;
    }

    /** 该绑定自关联以来是否至少被使用过一次。 */
    public boolean hasBeenUsed() {
        return lastUsedAt != null;
    }
}
