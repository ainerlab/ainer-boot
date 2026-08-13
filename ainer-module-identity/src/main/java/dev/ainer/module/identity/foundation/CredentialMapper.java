package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * MyBatis mapper for {@code ainer_identity_credential} (Greenfield foundation persistence, S2).
 */
@Mapper
public interface CredentialMapper {

    UUID selectUuidV7();

    int insertCredential(CredentialRow row);

    CredentialRow selectActiveByAccountAndType(
            @Param("accountId") UUID accountId,
            @Param("type") String type);

    /** Marks the current ACTIVE material for (account, type) as REVOKED. Returns rows affected (0 or 1). */
    int revokeActiveByAccountAndType(
            @Param("accountId") UUID accountId,
            @Param("type") String type,
            @Param("rotatedAt") java.time.Instant rotatedAt);
}