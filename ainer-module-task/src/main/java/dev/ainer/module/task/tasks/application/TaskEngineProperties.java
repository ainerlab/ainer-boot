package dev.ainer.module.task.tasks.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务执行引擎配置（ADR-0047）。构造器绑定 + 非法值钳制默认：
 * 未配置或非法的键自动落到文档化默认值。引擎开关由
 * {@code ainer.task.engine.enabled} 条件装配承担，不在此重复。
 *
 * @param pollIntervalMs 轮询间隔（毫秒）
 * @param batchSize      每次领取的最大任务数
 * @param retryBaseMs    指数退避基数（毫秒）；第 n 次失败后等待 base × 2^(n-1)，上限 max
 * @param retryMaxMs     指数退避上限（毫秒）
 * @param zombieCutoffMultiplier 僵尸 RUNNING 判定倍数：{@code locked_at} 早于
 *                    「定义 {@code timeout_seconds} × 此倍数」即重置回 PENDING
 */
@ConfigurationProperties("ainer.task.engine")
public record TaskEngineProperties(
        long pollIntervalMs,
        int batchSize,
        long retryBaseMs,
        long retryMaxMs,
        int zombieCutoffMultiplier) {

    public TaskEngineProperties {
        if (pollIntervalMs < 100) {
            pollIntervalMs = 5000;
        }
        if (batchSize < 1 || batchSize > 100) {
            batchSize = 10;
        }
        if (retryBaseMs < 1000) {
            retryBaseMs = 10_000;
        }
        if (retryMaxMs < retryBaseMs) {
            retryMaxMs = 3_600_000;
        }
        if (zombieCutoffMultiplier < 2) {
            zombieCutoffMultiplier = 3;
        }
    }
}
