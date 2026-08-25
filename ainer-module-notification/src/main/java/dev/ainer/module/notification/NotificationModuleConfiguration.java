package dev.ainer.module.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ainer.module.notification.notification.application.NotificationWebhookProperties;
import dev.ainer.module.notification.notification.infrastructure.HttpWebhookChannelSender;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * 通知中心的模块配置（ADR-0038）。默认启用，可通过 {@code ainer.notification.enabled=false}
 * 关闭。
 *
 * <p>架构要点：
 * <ul>
 *   <li>PG {@code SKIP LOCKED} 实现无锁队列领取——无需外部 MQ；</li>
 *   <li>虚拟线程 + {@code StructuredTaskScope} 实现有界并发投递；</li>
 *   <li>switch 模式匹配实现类型安全的渠道路由；</li>
 *   <li>可选的 HTTP webhook 投递（默认关闭，日志 sender 兜底）；</li>
 *   <li>JSONB 模板变量（PG 18）。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = NotificationFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = NotificationFeatureMarker.class, annotationClass = Mapper.class))
@EnableConfigurationProperties(NotificationWebhookProperties.class)
public class NotificationModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock notificationClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "webhookSender")
    @ConditionalOnProperty(prefix = "ainer.notification.webhook", name = "enabled", havingValue = "true")
    HttpWebhookChannelSender webhookSender(
            RestClient.Builder restClientBuilder,
            ObjectProvider<ObjectMapper> objectMapper,
            NotificationWebhookProperties properties) {
        return new HttpWebhookChannelSender(
                restClientBuilder, objectMapper.getIfAvailable(ObjectMapper::new), properties);
    }
}
