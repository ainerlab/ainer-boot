package dev.ainer.module.config;

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
 * Module configuration for the dynamic config slice (ADR-0038). Enabled by default; disable with
 * {@code ainer.config.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = ConfigFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = ConfigFeatureMarker.class, annotationClass = Mapper.class))
public class ConfigModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock configClock() {
        return Clock.systemUTC();
    }
}
