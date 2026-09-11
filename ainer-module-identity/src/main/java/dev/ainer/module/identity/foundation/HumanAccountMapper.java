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

    /**
     * 带期望态的条件状态迁移，并在同一条 UPDATE 中递增 {@code security_epoch}。
     *
     * <p>并发语义：行级写锁 + 期望态比较（compare-and-set）。当前状态不等于
     * {@code expectedStatus} 时不写任何行并返回 0，由调用方失败关闭；两个并发迁移因此
     * 只有一个能成功，另一个读到 0 行，不存在"后写覆盖先写"。所以 epoch 与状态永远
     * 一起前进，不可能只改状态或只加 epoch。
     */
    int transitionStatusAndIncrementEpoch(
            @Param("accountId") UUID accountId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus);

    /**
     * 在账号仍处于期望态时原子递增 {@code security_epoch}（密码轮换、凭据撤销等
     * 不改变状态的安全变更）。期望态不匹配时返回 0。
     */
    int incrementSecurityEpoch(
            @Param("accountId") UUID accountId,
            @Param("expectedStatus") String expectedStatus);
}
