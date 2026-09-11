package dev.ainer.module.notification.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 通知投递引擎的运行时约束（ADR-0038 加固）。
 *
 * <ul>
 *   <li>{@code send-timeout}——单个批次的投递上限：批次内所有发送在虚拟线程上并发执行，
 *       调度线程最多等待这个时长。超时的记录按失败处理走既有重试/终态语义；
 *       没有它，一条卡死的 SMTP 连接会把调度线程永久挂住。</li>
 *   <li>{@code lease-duration}——领取租约时长，必须大于 {@code send-timeout}：租约未过期的
 *       {@code SENDING} 记录不会被再次领取（防止重复投递），租约过期后才允许重新领取
 *       （覆盖实例崩溃或发送线程卡死）。</li>
 * </ul>
 *
 * <p>非法值回落到默认值；{@code lease-duration <= send-timeout} 属于「租约还没到期就被
 * 判定为可重新领取」的错误配置，启动时失败关闭。
 */
@ConfigurationProperties("ainer.notification.delivery")
public record NotificationDeliveryProperties(Duration sendTimeout, Duration leaseDuration) {

    private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(2);

    public NotificationDeliveryProperties {
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            sendTimeout = DEFAULT_SEND_TIMEOUT;
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            leaseDuration = DEFAULT_LEASE_DURATION;
        }
        if (leaseDuration.compareTo(sendTimeout) <= 0) {
            throw new IllegalStateException(
                    "ainer.notification.delivery.lease-duration must be greater than send-timeout");
        }
    }
}
