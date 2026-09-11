package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ServicePrincipal} 的持久化端口（ADR-0033 Greenfield §2.6）。
 *
 * <p>生产实现基于重建后的 Identity 基线（PostgreSQL UUIDv7 主键、按权威限定的查询）。
 * 端口只暴露类型化聚合；调用方绝不消费原始存储。按 OAuth {@code client_id} 解析会在
 * 内部跨越 {@link OAuthClientBindingRepository} 边界，使 token 签发能把可轮换凭证投影为
 * 稳定的 {@code ServiceSubjectRef}。
 */
public interface ServicePrincipalRepository {

    void save(ServicePrincipal principal);

    Optional<ServicePrincipal> findByPrincipalId(UUID principalId);

    /**
     * 通过当前 ACTIVE 绑定解析 OAuth client_id 背后的主体。凭证不存在活跃绑定时返回 empty。
     */
    Optional<ServicePrincipal> findByActiveClientId(String clientId);

    /** principal 聚合的下一个 PostgreSQL UUIDv7 主键。 */
    UUID nextUuidV7();

    /**
     * 带期望态的条件状态迁移：只有当前状态等于 {@code expectedStatus} 时才写入
     * {@code targetStatus}，并在同一条 UPDATE 中递增 {@code security_epoch}。
     * 返回受影响行数（0 或 1）；0 表示期望态不成立，调用方失败关闭。
     */
    int transitionStatus(
            UUID principalId, ServicePrincipalStatus expectedStatus, ServicePrincipalStatus targetStatus);
}
