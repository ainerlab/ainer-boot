package dev.ainer.module.notification.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Webhook 渠道的可选真实投递（ADR-0040 SPI 可替换）。默认关闭，保留
 * {@code LoggingChannelSender.Webhook} 作开发兜底。启用时必须配置 host 白名单，
 * 否则启动失败关闭。
 */
@ConfigurationProperties("ainer.notification.webhook")
public record NotificationWebhookProperties(
        boolean enabled,
        List<String> allowedHosts,
        Duration connectTimeout,
        Duration readTimeout,
        boolean allowInsecureHttp) {

    public NotificationWebhookProperties {
        allowedHosts = normalizeHosts(allowedHosts);
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            readTimeout = Duration.ofSeconds(2);
        }
        if (enabled && allowedHosts.isEmpty()) {
            throw new IllegalStateException(
                    "ainer.notification.webhook.allowed-hosts must be set when webhook delivery is enabled");
        }
    }

    private static List<String> normalizeHosts(List<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return List.of();
        }
        return hosts.stream()
                .filter(host -> host != null && !host.isBlank())
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
