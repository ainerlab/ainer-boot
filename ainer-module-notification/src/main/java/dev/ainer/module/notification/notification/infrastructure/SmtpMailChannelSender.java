package dev.ainer.module.notification.notification.infrastructure;

import dev.ainer.module.notification.notification.application.EmailAddressRules;
import dev.ainer.module.notification.notification.application.NotificationEmailProperties;
import dev.ainer.module.notification.notification.domain.ChannelSender;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * EMAIL 渠道的 SMTP 投递。不把收件人、主题或正文写入日志或异常消息。
 */
public final class SmtpMailChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailChannelSender.class);

    private final JavaMailSender mailSender;
    private final NotificationEmailProperties properties;

    public SmtpMailChannelSender(JavaMailSender mailSender, NotificationEmailProperties properties) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String recipient, String title, String body) {
        String to = EmailAddressRules.validate(recipient);
        String subject = EmailAddressRules.requireSafeText(title == null ? "" : title);
        String text = body == null ? "" : body;
        if (text.length() > 100_000) {
            throw new IllegalStateException("Email delivery failed");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("邮件投递失败");
            throw new IllegalStateException("Email delivery failed");
        } catch (Exception exception) {
            log.warn("邮件投递失败");
            throw new IllegalStateException("Email delivery failed");
        }
    }
}
