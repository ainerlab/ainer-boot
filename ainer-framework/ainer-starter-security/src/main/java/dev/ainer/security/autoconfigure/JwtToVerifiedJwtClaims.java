package dev.ainer.security.autoconfigure;

import dev.ainer.security.token.VerifiedJwtClaims;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 把 Spring Security 已验证的 {@link Jwt} 转换为 Foundation 的
 * {@link VerifiedJwtClaims} 输入契约。转换不做任何信任判断——签名、签发方与
 * audience 验证在此之前已由安全链完成。
 */
final class JwtToVerifiedJwtClaims {

    private JwtToVerifiedJwtClaims() {
    }

    static VerifiedJwtClaims from(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        if (jwt.getIssuer() == null || jwt.getExpiresAt() == null) {
            throw new IllegalArgumentException("Verified JWT issuer and expiry are required");
        }
        Set<String> audiences = jwt.getAudience() == null
                ? Set.of()
                : new LinkedHashSet<>(jwt.getAudience());
        return new VerifiedJwtClaims(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                audiences,
                jwt.getExpiresAt(),
                jwt.getClaims());
    }
}
