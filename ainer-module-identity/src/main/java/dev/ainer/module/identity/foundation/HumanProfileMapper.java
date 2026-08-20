package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * {@code ainer_identity_human_profile} 的 MyBatis mapper（Greenfield foundation 持久化，S2）。
 */
@Mapper
public interface HumanProfileMapper {

    HumanProfileRow selectByAccountId(@Param("accountId") UUID accountId);

    int insertProfile(HumanProfileRow row);

    int updateProfile(HumanProfileRow row);
}