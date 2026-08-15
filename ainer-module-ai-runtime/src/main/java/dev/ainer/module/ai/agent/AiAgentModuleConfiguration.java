package dev.ainer.module.ai.agent;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Agent definition registry configuration (ADR-0043 A1). Independent of the model gateway:
 * enabled by default ({@code ainer.ai.agents.enabled=false} disables), so hosts can offer the
 * delegation registry without enabling gateway invocation.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.ai.agents", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = AiAgentFeatureMarker.class)
@MapperScan(basePackageClasses = AiAgentFeatureMarker.class, annotationClass = Mapper.class)
public class AiAgentModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock agentClock() {
        return Clock.systemUTC();
    }
}
