package dev.ainer.security.principal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 指向某个 {@link IdentityAuthorityRef}（身份权威）内稳定非人类 ServicePrincipal 的引用
 * （ADR-0033 Greenfield §2.6）。
 *
 * <p>{@code ServicePrincipal} 是非人类调用方的稳定身份；OAuth {@code client_id} 是绑定到它
 * 之上的可轮换凭证/客户端标识，而不是主体本身。客户端轮换不得改变审计身份；Service
 * 绝不能持有人类的 WorkspaceMembership 或治理 OWNER 角色。
 */
public record ServiceSubjectRef(IdentityAuthorityRef authority, String servicePrincipalId)
        implements PrincipalSubjectRef {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public ServiceSubjectRef {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(servicePrincipalId, "servicePrincipalId");
        String normalized = servicePrincipalId.trim();
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("servicePrincipalId is invalid");
        }
        servicePrincipalId = normalized;
    }

    @Override
    public String subjectId() {
        return servicePrincipalId;
    }
}
