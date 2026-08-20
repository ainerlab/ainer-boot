package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * sealed 通知意图——调用方想发送什么。为分发层的 record 模式解构而设计（JDK 25）：
 * {@code switch (intent) { case TemplateIntent t -> ...; case DirectIntent d -> ...; }}。
 *
 * <p>两个实现：
 * <ul>
 *   <li>{@link TemplateIntent}——按编码渲染模板并绑定变量；</li>
 *   <li>{@link DirectIntent}——不经模板直接发送 title/body。</li>
 * </ul>
 */
public sealed interface NotificationIntent permits NotificationIntent.TemplateIntent, NotificationIntent.DirectIntent {

    NotificationChannel channel();
    String recipient();

    /** 按编码渲染模板并绑定 JSONB 变量。 */
    record TemplateIntent(
            NotificationChannel channel,
            String recipient,
            String templateCode,
            Map<String, Object> variables) implements NotificationIntent {

        public TemplateIntent {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(templateCode, "templateCode");
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }
    }

    /** 不经模板直接发送原始消息。 */
    record DirectIntent(
            NotificationChannel channel,
            String recipient,
            String title,
            String body,
            @Nullable Map<String, Object> payload) implements NotificationIntent {

        public DirectIntent {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            payload = payload == null ? null : Map.copyOf(payload);
        }
    }
}
