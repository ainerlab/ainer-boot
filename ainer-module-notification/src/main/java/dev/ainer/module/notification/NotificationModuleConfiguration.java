package dev.ainer.module.notification;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module configuration for the notification center (ADR-0038). Enabled by default; disable with
 * {@code ainer.notification.enabled=false}.
 *
 * <p>Architecture highlights:
 * <ul>
 *   <li>PG {@code SKIP LOCKED} for lock-free queue claiming — no external MQ needed;</li>
 *   <li>Virtual threads + {@code StructuredTaskScope} for bounded concurrent delivery;</li>
 *   <li>Switch pattern matching for type-safe channel dispatch;</li>
 *   <li>Spring {@code RestClient} (Framework 7) for HTTP webhook delivery;</li>
 *   <li>JSONB template variables (PG 18).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = NotificationFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = NotificationFeatureMarker.class, annotationClass = Mapper.class))
public class NotificationModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
