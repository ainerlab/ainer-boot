package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Sealed notification intent — what the caller wants to send. Designed for record pattern
 * deconstruction in the dispatch layer (JDK 25):
 * {@code switch (intent) { case TemplateIntent t -> ...; case DirectIntent d -> ...; }}.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link TemplateIntent} — render a template by code with variable bindings;</li>
 *   <li>{@link DirectIntent} — send a raw title/body without a template.</li>
 * </ul>
 */
public sealed interface NotificationIntent permits NotificationIntent.TemplateIntent, NotificationIntent.DirectIntent {

    NotificationChannel channel();
    String recipient();

    /** Render a template by code with JSONB variable bindings. */
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

    /** Send a raw message without a template. */
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
