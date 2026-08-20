package dev.ainer.security.token;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 提交给 {@link TokenProfileResolver} 的最小化、已完成验证的 JWT claim 面。
 *
 * <p>这只是输入契约：签名、签发方与 audience 信任在本值构建之前（由授权服务器 /
 * Resource Server 安全链）建立。解析器消费这些类型化字段加上原始 claims 的只读视图，
 * 投影出 {@link AuthenticatedPrincipal}；业务代码绝不直接接收本类型。
 */
public record VerifiedJwtClaims(
        String issuer,
        String subject,
        Set<String> audiences,
        Instant expiresAt,
        Map<String, Object> claims) {

    public VerifiedJwtClaims {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(audiences, "audiences");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(claims, "claims");
        if (issuer.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("issuer and subject must be non-blank");
        }
        audiences = Set.copyOf(audiences);
        claims = Map.copyOf(claims);
    }

    /**
     * 按指定类型读取原始 claim，不存在时返回 {@code null}。调用方不得依赖此方法做
     * 安全相关决策；解析器是解释 claims 的唯一权威。
     */
    public @Nullable Object claim(String name) {
        return claims.get(name);
    }
}
