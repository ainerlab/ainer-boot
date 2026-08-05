package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * MyBatis mapper for {@code ainer_identity_human_account} (Greenfield foundation persistence, S1.2).
 * Uses the project's plain-MyBatis style (no MyBatis-Plus {@code BaseMapper}); {@code @Mapper} makes it
 * self-discoverable independent of any {@code @MapperScan} base package.
 */
@Mapper
public interface HumanAccountMapper {

    UUID selectUuidV7();

    int insertAccount(HumanAccountRow row);

    HumanAccountRow selectByAccountId(@Param("accountId") UUID accountId);
}
