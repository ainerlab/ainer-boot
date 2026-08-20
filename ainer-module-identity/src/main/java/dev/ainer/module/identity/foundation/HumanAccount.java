package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单一 {@link IdentityAuthorityRef}（身份权威）内的自然人安全账号生命周期根
 * （ADR-0033 Greenfield §3）。
 *
 * <p>HumanAccount 不是全局自然人主档，不是登录标识符，也不是 Tenant 成员关系。一个自然人
 * 可以跨 authority/realm 合法持有多个 HumanAccount；相同的邮箱、手机号或用户名绝不自动
 * 合并。账号可以在零个 Workspace 成员关系下存在；禁用或关闭它绝不会级联删除 Workspace、
 * 内容或审计。
 *
 * <p>{@code securityEpoch} 是单调递增的账号级 revocation 版本：早于当前 epoch 签发的
 * 凭证、会话与 token 全部失效。
 */
public record HumanAccount(
        UUID accountId,
        IdentityAuthorityRef authority,
        AccountStatus status,
        long securityEpoch,
        Instant createdAt) {

    public HumanAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (securityEpoch < 0) {
            throw new IllegalArgumentException("securityEpoch must be non-negative");
        }
    }

    /**
     * 供授权、审计与资源归因使用的、带权威限定的主体引用。
     */
    public HumanSubjectRef toSubjectRef() {
        return new HumanSubjectRef(authority, accountId.toString());
    }
}
