package dev.ainer.security.principal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 指向某个 {@link IdentityAuthorityRef}（身份权威）内自然人安全账号的引用
 * （ADR-0033 Greenfield §3）。
 *
 * <p>{@code accountId} 是 HumanAccount ID，不是全局自然人主档，不是登录标识符，
 * 也不是 Tenant 成员关系。同一个自然人可以在不同 authority、realm 或部署下合法持有
 * 多个 HumanAccount；相同的邮箱、手机号或用户名绝不自动合并。
 */
public record HumanSubjectRef(IdentityAuthorityRef authority, String accountId)
        implements PrincipalSubjectRef {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public HumanSubjectRef {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(accountId, "accountId");
        String normalized = accountId.trim();
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("accountId is invalid");
        }
        accountId = normalized;
    }

    @Override
    public String subjectId() {
        return accountId;
    }
}
