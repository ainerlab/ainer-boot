package dev.ainer.server.security;

import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 决策审计保留任务配置（{@code ainer.authorization.decision-audit-retention.*}）。
 *
 * <p>决策审计热表 {@code ainer_authorization_decision_audit} 是 append-only 且每个带
 * {@code @AinerAuthorize} 的请求都会写一行，长期运行必然无限增长。保留任务把超过
 * {@link #getHotRetention()} 的行批量搬进归档表，在线历史查询读热+冷并集（见
 * {@code AuthorizationDecisionAuditLifecycleService}）。
 *
 * <p>默认 {@code enabled=false}：归档是运维动作，首次上线必须先按 {@code docs/operations.md}
 * 在接近真实规模的数据库上验证批次、锁等待与 WAL 之后才启用。所有时长必须为正；
 * {@code oldest-hot-warn-window} 必须严格大于 {@code hot-retention}（否则该告警在任何正常
 * 运行下都会立刻常亮，等于没有告警），由 {@code AuthorizationDecisionAuditRetentionConfiguration}
 * 在启动时校验。
 */
@Validated
@ConfigurationProperties("ainer.authorization.decision-audit-retention")
public class AuthorizationDecisionAuditRetentionProperties {

    /** 允许归档复制到外部的热表保留期：{@code evaluated_at} 早于 {@code now - hot-retention} 的行会被归档。 */
    @DurationMin(nanos = 1)
    private final Duration hotRetention;

    /** 相邻两次归档周期的间隔（上一周期结束后计算）。 */
    @DurationMin(nanos = 1)
    private final Duration fixedDelay;

    /** 首次执行延迟：必须声明，否则启动瞬间就会抢跑并与测试/启动期负载争抢行锁。 */
    @DurationMin(nanos = 1)
    private final Duration initialDelay;

    /** 单批搬运上限（1..5000）：限制单个归档事务的持锁时间与 WAL 量。 */
    @Min(1)
    private final int batchSize;

    /** 单周期最多连续搬运多少批（1..1000）：兼顾追赶速度与单周期资源占用。 */
    @Min(1)
    private final int maxBatchesPerCycle;

    /** 最久未归档告警窗口：最旧热行年龄超过它即 WARN，必须严格大于 {@link #hotRetention}。 */
    @DurationMin(nanos = 1)
    private final Duration oldestHotWarnWindow;

    private final boolean enabled;

    public AuthorizationDecisionAuditRetentionProperties(
            boolean enabled,
            Duration hotRetention,
            Duration fixedDelay,
            Duration initialDelay,
            Duration oldestHotWarnWindow,
            Integer batchSize,
            Integer maxBatchesPerCycle) {
        this.enabled = enabled;
        this.hotRetention = hotRetention != null ? hotRetention : Duration.ofDays(90);
        this.fixedDelay = fixedDelay != null ? fixedDelay : Duration.ofMinutes(5);
        this.initialDelay = initialDelay != null ? initialDelay : Duration.ofMinutes(5);
        this.oldestHotWarnWindow = oldestHotWarnWindow != null ? oldestHotWarnWindow : Duration.ofDays(91);
        this.batchSize = batchSize != null ? batchSize : 500;
        this.maxBatchesPerCycle = maxBatchesPerCycle != null ? maxBatchesPerCycle : 20;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getHotRetention() {
        return hotRetention;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxBatchesPerCycle() {
        return maxBatchesPerCycle;
    }

    public Duration getOldestHotWarnWindow() {
        return oldestHotWarnWindow;
    }
}
