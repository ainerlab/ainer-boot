package dev.ainer.module.identity.foundation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link LoginIdentity} 绑定的持久化端口（ADR-0033 Greenfield §4）。
 *
 * <p>登录查找键是 {@code (type, providerAuthority, normalizedIdentifier)}：认证把一个
 * 凭证解析到至多一个绑定，该绑定再精确引用一个 {@link HumanAccount}。不同 provider /
 * authority 下的相同原始标识符是不同绑定，绝不等价合并。
 */
public interface LoginIdentityRepository {

    void save(LoginIdentity identity);

    Optional<LoginIdentity> findByTypeAndIdentifier(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier);

    List<LoginIdentity> findByAccount(UUID accountId);
}
