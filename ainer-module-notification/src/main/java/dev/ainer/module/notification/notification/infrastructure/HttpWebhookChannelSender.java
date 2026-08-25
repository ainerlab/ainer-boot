package dev.ainer.module.notification.notification.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ainer.module.notification.notification.application.NotificationWebhookProperties;
import dev.ainer.module.notification.notification.application.WebhookDestinationRules;
import dev.ainer.module.notification.notification.domain.ChannelSender;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WEBHOOK 渠道的 HTTP 投递：对 recipient URL 做 POST JSON {@code title}/{@code body}。
 * 不跟随重定向；不把 URL、正文或供应商响应写入日志或异常消息。
 */
public final class HttpWebhookChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookChannelSender.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NotificationWebhookProperties properties;

    public HttpWebhookChannelSender(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            NotificationWebhookProperties properties) {
        Objects.requireNonNull(restClientBuilder, "restClientBuilder");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    public void send(String recipient, String title, String body) {
        URI uri = WebhookDestinationRules.validate(recipient, properties);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", title == null ? "" : title);
        payload.put("body", body == null ? "" : body);
        try {
            restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            log.warn("Webhook 投递失败: host={}, status={}", uri.getHost(), exception.getStatusCode().value());
            throw new IllegalStateException(
                    "Webhook delivery failed: HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("Webhook 投递失败: host={}", uri.getHost());
            throw new IllegalStateException("Webhook delivery failed");
        } catch (Exception exception) {
            log.warn("Webhook 投递失败: host={}", uri.getHost());
            throw new IllegalStateException("Webhook delivery failed");
        }
    }
}
