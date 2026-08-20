package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * {@code ainer_identity_login_identity} 的 MyBatis mapper（Greenfield foundation 持久化，S1.2）。
 */
@Mapper
public interface LoginIdentityMapper {

    int insertLogin(LoginIdentityRow row);

    LoginIdentityRow selectByTypeAndIdentifier(
            @Param("type") String type,
            @Param("providerAuthority") String providerAuthority,
            @Param("normalizedIdentifier") String normalizedIdentifier);

    List<LoginIdentityRow> selectByAccount(@Param("accountId") UUID accountId);
}
