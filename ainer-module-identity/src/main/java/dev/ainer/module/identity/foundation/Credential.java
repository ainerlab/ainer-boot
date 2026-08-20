package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 绑定到 {@link HumanAccount} 的凭证材料（ADR-0033 Greenfield §4，执行计划 缺口 A）。
 *
 * <p>这就是 {@link LoginIdentity} 刻意"引用而不存储"的专用凭证存储：不透明的
 * {@code credentialData}（密码哈希、WebAuthn 公钥引用或 OIDC subject）保存在这里，
 * 绝不出现在绑定上。认证时先由 LoginIdentity 解析到账号，再读取该账号的 ACTIVE
 * {@link CredentialType} 材料。
 *
 * <p>材料必须轮换而不是原地修改：轮换把旧的 ACTIVE 凭证置为 {@code REVOKED} 并插入
 * 新的 ACTIVE 凭证，因此任一时刻一个账号每种类型至多有一份 ACTIVE 材料。密码哈希在
 * 进入本 record 之前由项目的委托式 {@code PasswordEncoder} 编码。
 */
public record Credential(
        UUID credentialId,
        UUID accountId,
        CredentialType type,
        String credentialData,
        CredentialStatus status,
        Instant createdAt,
        @Nullable Instant rotatedAt) {

    public Credential {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(credentialData, "credentialData");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (credentialData.isBlank()) {
            throw new IllegalArgumentException("credentialData must be non-blank");
        }
        if (status == CredentialStatus.ACTIVE && rotatedAt != null) {
            throw new IllegalArgumentException("ACTIVE credential must not carry a rotatedAt");
        }
    }

    /** 该材料当前是否可用于认证。 */
    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }
}