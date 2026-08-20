package dev.ainer.module.dictionary;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 字典切片的模块配置（ADR-0038）。由宿主应用通过 {@code @Import} 装配。
 * 默认启用，可通过 {@code ainer.dictionary.enabled=false} 关闭。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.dictionary", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = DictionaryFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = DictionaryFeatureMarker.class, annotationClass = Mapper.class))
public class DictionaryModuleConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    Clock dictionaryClock() {
        return Clock.systemUTC();
    }
}
