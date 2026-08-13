package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * MyBatis mapper for {@code ainer_identity_human_profile} (Greenfield foundation persistence, S2).
 */
@Mapper
public interface HumanProfileMapper {

    HumanProfileRow selectByAccountId(@Param("accountId") UUID accountId);

    int insertProfile(HumanProfileRow row);

    int updateProfile(HumanProfileRow row);
}