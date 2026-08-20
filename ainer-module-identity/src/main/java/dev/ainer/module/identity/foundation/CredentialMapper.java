package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * {@code ainer_identity_credential} 的 MyBatis mapper（Greenfield foundation 持久化，S2）。
 */
@Mapper
public interface CredentialMapper {

    UUID selectUuidV7();

    int insertCredential(CredentialRow row);

    CredentialRow selectActiveByAccountAndType(
            @Param("accountId") UUID accountId,
            @Param("type") String type);

    /** 把 (account, type) 当前的 ACTIVE 材料置为 REVOKED。返回受影响行数（0 或 1）。 */
    int revokeActiveByAccountAndType(
            @Param("accountId") UUID accountId,
            @Param("type") String type,
            @Param("rotatedAt") java.time.Instant rotatedAt);
}