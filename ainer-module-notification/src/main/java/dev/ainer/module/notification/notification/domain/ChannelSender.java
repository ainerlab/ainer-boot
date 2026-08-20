package dev.ainer.module.notification.notification.domain;

/**
 * 通过特定渠道发送通知的 SPI（ADR-0038）。产品为每个渠道提供实现
 * （短信网关、SMTP 邮件、push 服务、webhook）。默认的内存 noop sender 用于测试；
 * 生产环境使用真实适配器。
 *
 * <p>sender 运行在虚拟线程上——实现可以自由阻塞（HTTP 调用、SMTP），
 * 不会阻塞平台线程。
 */
public interface ChannelSender {

    /**
     * 该 sender 负责的渠道。用于 switch 模式路由：
     * {@code switch (sender.channel()) { case SMS -> ...; case EMAIL -> ...; }}。
     */
    NotificationChannel channel();

    /**
     * 同步发送一条通知。失败时抛出——重试由调用方处理。
     *
     * @param recipient 目标地址（手机号、邮箱、设备 token、URL）
     * @param title     消息标题（短信渠道可为 null）
     * @param body      消息正文
     */
    void send(String recipient, String title, String body);

    /**
     * 单次发送尝试的结果，用于结构化错误处理。
     */
    record SendResult(boolean success, @org.jspecify.annotations.Nullable String errorMessage) {
        public static SendResult ok() { return new SendResult(true, null); }
        public static SendResult fail(String error) { return new SendResult(false, error); }
    }
}
