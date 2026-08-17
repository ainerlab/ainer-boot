package dev.ainer.module.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Knowledge Foundation configuration (ADR-0044). Default on; disable with ainer.knowledge.enabled=false. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.knowledge", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = KnowledgeFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = KnowledgeFeatureMarker.class, annotationClass = Mapper.class))
public class KnowledgeModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock knowledgeClock() {
        return Clock.systemUTC();
    }
}
