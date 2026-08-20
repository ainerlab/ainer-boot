package dev.ainer.security.principal;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 账号 / ServicePrincipal ID 的解释作用域（ADR-0033 Greenfield §2.2）。
 *
 * <p>{@code IdentityAuthorityRef} 为稳定 ID 附加限定条件，使不同信任域签发的相同原始标识符
 * 绝不会被当作同一对象。它是值对象；v1 可以从可信的 {@code iss} claim 解析得到，
 * 不需要持久化聚合根。
 *
 * <p>{@code issuer} 是规范化可信签发方（例如 OAuth {@code iss} URL）。{@code realm} 是可选的
 * 区分器，仅当同一签发方服务多个 realm、部署或私有实例时使用；缺失表示仅凭 issuer 即可
 * 标识该权威。
 *
 * <p>它刻意不是新的 Tenant：不拥有 Workspace、成员、套餐、合同、组织或任何隔离域。
 * 裸的 {@code sub}、邮箱、手机号或光板 UUID 绝不隐含任何权威。
 */
public record IdentityAuthorityRef(String issuer, @Nullable String realm) {

    private static final int ISSUER_MAX_LENGTH = 256;
    private static final int REALM_MAX_LENGTH = 128;

    public IdentityAuthorityRef {
        Objects.requireNonNull(issuer, "issuer");
        String normalizedIssuer = issuer.trim();
        if (normalizedIssuer.isEmpty() || normalizedIssuer.length() > ISSUER_MAX_LENGTH) {
            throw new IllegalArgumentException("issuer must be a non-blank string of at most "
                    + ISSUER_MAX_LENGTH + " characters");
        }
        issuer = normalizedIssuer;
        if (realm != null) {
            String normalizedRealm = realm.trim();
            if (normalizedRealm.isEmpty() || normalizedRealm.length() > REALM_MAX_LENGTH) {
                throw new IllegalArgumentException("realm must be a non-blank string of at most "
                        + REALM_MAX_LENGTH + " characters when present");
            }
            realm = normalizedRealm;
        }
    }

    /**
     * 常见单 realm 场景的便捷构造器。
     */
    public IdentityAuthorityRef(String issuer) {
        this(issuer, null);
    }

    public boolean hasRealm() {
        return realm != null;
    }
}
