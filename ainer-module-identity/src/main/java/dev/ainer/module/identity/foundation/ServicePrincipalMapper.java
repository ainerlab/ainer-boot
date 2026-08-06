package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * MyBatis mapper for {@code ainer_identity_service_principal} (Greenfield foundation persistence, S1.1 spine).
 * Uses the project's plain-MyBatis style (no MyBatis-Plus {@code BaseMapper}); {@code @Mapper} makes it
 * self-discoverable independent of any {@code @MapperScan} base package.
 */
@Mapper
public interface ServicePrincipalMapper {

    UUID selectUuidV7();

    int insertPrincipal(ServicePrincipalRow row);

    ServicePrincipalRow selectByPrincipalId(@Param("principalId") UUID principalId);

    /**
     * Resolve the principal backing an OAuth client_id via its currently ACTIVE binding. Used by the token
     * issuance path to project a stable {@code ServiceSubjectRef} from a rotatable credential.
     */
    ServicePrincipalRow selectByActiveClientId(@Param("clientId") String clientId);
}
