package dev.ainer.module.notification.notification.domain;

/**
 * SPI for sending a notification via a specific channel (ADR-0038). Products implement this for
 * each channel (SMS gateway, SMTP email, push service, webhook). The default in-memory noop sender
 * is for testing; production uses real adapters.
 *
 * <p>Senders run on virtual threads — implementations may block freely (HTTP calls, SMTP) without
 * blocking platform threads.
 */
public interface ChannelSender {

    /**
     * Which channel this sender handles. Used for switch pattern dispatch:
     * {@code switch (sender.channel()) { case SMS -> ...; case EMAIL -> ...; }}.
     */
    NotificationChannel channel();

    /**
     * Send a notification synchronously. Throws on failure — the caller handles retry.
     *
     * @param recipient target address (phone, email, device token, URL)
     * @param title     message title (may be null for SMS)
     * @param body      message body
     */
    void send(String recipient, String title, String body);

    /**
     * Result of a send attempt, for structured error handling.
     */
    record SendResult(boolean success, @org.jspecify.annotations.Nullable String errorMessage) {
        public static SendResult ok() { return new SendResult(true, null); }
        public static SendResult fail(String error) { return new SendResult(false, error); }
    }
}
