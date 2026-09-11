package dev.ainer.module.identity.foundation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * {@code ainer_identity_service_principal} 的 MyBatis mapper（Greenfield foundation 持久化，
 * S1.1 主干）。采用项目统一的纯 MyBatis 风格（不用 MyBatis-Plus 的 {@code BaseMapper}）；
 * {@code @Mapper} 使其可自发现，不依赖任何 {@code @MapperScan} 基包。
 */
@Mapper
public interface ServicePrincipalMapper {

    UUID selectUuidV7();

    int insertPrincipal(ServicePrincipalRow row);

    ServicePrincipalRow selectByPrincipalId(@Param("principalId") UUID principalId);

    /**
     * 通过当前 ACTIVE 绑定解析 OAuth client_id 背后的 principal。供 token 签发路径使用，
     * 把可轮换凭证投影为稳定的 {@code ServiceSubjectRef}。
     */
    ServicePrincipalRow selectByActiveClientId(@Param("clientId") String clientId);

    /**
     * 带期望态的条件状态迁移，并在同一条 UPDATE 中递增 {@code security_epoch}。
     * 当前状态不等于 {@code expectedStatus} 时不写任何行并返回 0（调用方失败关闭）。
     */
    int transitionStatusAndIncrementEpoch(
            @Param("principalId") UUID principalId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus);
}
