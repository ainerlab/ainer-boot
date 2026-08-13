package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * MyBatis mapper for {@code ainer_identity_oauth_client_binding} (Greenfield foundation persistence, S1.1 spine).
 * Uses the project's plain-MyBatis style; {@code @Mapper} makes it self-discoverable.
 */
@Mapper
public interface OAuthClientBindingMapper {

    UUID selectUuidV7();

    int insertBinding(OAuthClientBindingRow row);

    OAuthClientBindingRow selectActiveByClientId(@Param("clientId") String clientId);

    OAuthClientBindingRow selectByPrincipalId(@Param("principalId") UUID principalId);
}
