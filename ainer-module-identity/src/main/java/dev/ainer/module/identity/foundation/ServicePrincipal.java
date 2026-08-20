package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单一 {@link IdentityAuthorityRef}（身份权威）内稳定的非人类安全主体
 * （ADR-0033 Greenfield §2.6）。
 *
 * <p>ServicePrincipal 是非人类调用方在审计上稳定的身份。OAuth {@code client_id} 是绑定到
 * 它之上的可轮换凭证（见 {@link OAuthClientBinding}），不是主体本身：客户端轮换绝不能
 * 改变审计身份。Service 绝不能持有人类的 WorkspaceMembership 或治理 OWNER 角色。
 *
 * <p>{@code securityEpoch} 与 {@link HumanAccount#securityEpoch()} 对应：单调递增的
 * principal 级 revocation 版本——早于当前 epoch 签发的凭证、会话与 token 全部失效。
 * 它是旧版把 {@code client_id} 混同为主体的服务客户端查找的 Greenfield 替代。
 */
public record ServicePrincipal(
        UUID principalId,
        IdentityAuthorityRef authority,
        ServicePrincipalStatus status,
        long securityEpoch,
        Instant createdAt) {

    public ServicePrincipal {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (securityEpoch < 0) {
            throw new IllegalArgumentException("securityEpoch must be non-negative");
        }
    }

    /**
     * 供 token 签发、授权与审计使用的、带权威限定的主体引用。{@code servicePrincipalId}
     * 是该主体的稳定 UUID 字符串，绝不是可轮换的 client_id。
     */
    public ServiceSubjectRef toSubjectRef() {
        return new ServiceSubjectRef(authority, principalId.toString());
    }
}
