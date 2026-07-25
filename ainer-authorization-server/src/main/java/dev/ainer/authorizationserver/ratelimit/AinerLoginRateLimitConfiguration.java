package dev.ainer.authorizationserver.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 登录限速装配（默认关闭）。见 ADR-0016。node-local 固定窗口限速器 + 浏览器链 filter。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.login-rate-limit",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(AinerLoginRateLimitProperties.class)
public class AinerLoginRateLimitConfiguration {

    @Bean
    AinerRateLimiter ainerLoginRateLimiter(AinerLoginRateLimitProperties properties, Clock clock) {
        return new AinerRateLimiter(properties.getWindow(), properties.getMaxRequests(), clock);
    }

    @Bean
    AinerLoginRateLimitFilter ainerLoginRateLimitFilter(
            AinerRateLimiter rateLimiter, AinerLoginRateLimitProperties properties) {
        return new AinerLoginRateLimitFilter(rateLimiter, properties.getPaths());
    }
}
