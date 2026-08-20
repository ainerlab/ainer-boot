package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link SubjectBinding} 聚合的持久化端口（ADR-0030 S1）。由基础设施层实现；由
 * {@link SubjectBindingApplicationService} 与 PostgreSQL 的 {@code BindingResolver} 消费。
 */
public interface SubjectBindingRepository {

    /**
     * 持久化 Binding，附带数据库身份、Role 引用与领域值。
     *
     * @param id          数据库生成的 UUIDv7 主键
     * @param subjectRef  Binding 的归属主体
     * @param roleId      该 Binding 引用的持久化 Role
     * @param scope       结构化 scope（Global / Workspace / Resource）
     * @param status      ACTIVE 或 REVOKED
     * @param validFrom   有效窗口起点（含）
     * @param validUntil  有效窗口终点（不含），开放式为 null
     * @param version     乐观并发版本
     * @param revokedAt   撤销时间，仍活跃时为 null
     * @param revokedReason 自由文本撤销原因，可为 null
     */
    record PersistedBinding(
            UUID id,
            SubjectRef subjectRef,
            UUID roleId,
            String roleCode,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version,
            Instant revokedAt,
            String revokedReason) {
    }

    UUID save(SubjectRef subject, UUID roleId, Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedBinding> findById(UUID id);

    /**
     * 逻辑撤销 {@code id} 对应的 Binding。把状态置为 REVOKED、记录撤销时间与原因并递增
     * 版本。Binding 不存在或已被撤销时返回空。
     *
     * @param id        Binding 主键
     * @param revokedAt 撤销时间戳
     * @param reason    存入审计轨迹的自由文本原因
     * @return 撤销成功时返回更新后的版本；不存在或已被撤销时返回空
     */
    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /**
     * 返回给定主体全部有效窗口覆盖 {@code at} 的 ACTIVE Binding。这是 PostgreSQL
     * {@code BindingResolver} 使用的查询；已撤销或已过期的 Binding 在数据库层就被排除
     * ——不存在 ALLOW 缓存。
     */
    List<PersistedBinding> findLiveBindings(SubjectRef subject, Instant at);

    /**
     * 返回给定主体全部 Binding（含已撤销/已过期），按创建时间排序。供管理查询使用，
     * 决策引擎不使用。
     */
    List<PersistedBinding> findAllBySubject(SubjectRef subject);
}
