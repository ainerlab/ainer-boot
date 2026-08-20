package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * {@code ainer_identity_human_account} 的 MyBatis mapper（Greenfield foundation 持久化，S1.2）。
 * 采用项目统一的纯 MyBatis 风格（不用 MyBatis-Plus 的 {@code BaseMapper}）；{@code @Mapper}
 * 使其可自发现，不依赖任何 {@code @MapperScan} 基包。
 */
@Mapper
public interface HumanAccountMapper {

    UUID selectUuidV7();

    int insertAccount(HumanAccountRow row);

    HumanAccountRow selectByAccountId(@Param("accountId") UUID accountId);
}
