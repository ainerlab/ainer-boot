package dev.ainer.module.organization;

import dev.ainer.module.organization.orgdir.application.OrganizationProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module configuration for the organization directory slice (ADR-0042). Enabled by default;
 * disable with {@code ainer.organization.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.organization", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = OrganizationFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = OrganizationFeatureMarker.class, annotationClass = Mapper.class))
@EnableConfigurationProperties(OrganizationProperties.class)
public class OrganizationModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock organizationClock() {
        return Clock.systemUTC();
    }
}
