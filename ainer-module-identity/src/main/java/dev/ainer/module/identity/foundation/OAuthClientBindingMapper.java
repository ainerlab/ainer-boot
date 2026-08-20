package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * {@code ainer_identity_oauth_client_binding} 的 MyBatis mapper（Greenfield foundation 持久化，
 * S1.1 主干）。采用项目统一的纯 MyBatis 风格；{@code @Mapper} 使其可自发现。
 */
@Mapper
public interface OAuthClientBindingMapper {

    UUID selectUuidV7();

    int insertBinding(OAuthClientBindingRow row);

    OAuthClientBindingRow selectActiveByClientId(@Param("clientId") String clientId);

    OAuthClientBindingRow selectByPrincipalId(@Param("principalId") UUID principalId);
}
