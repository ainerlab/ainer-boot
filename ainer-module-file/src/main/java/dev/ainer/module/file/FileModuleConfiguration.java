package dev.ainer.module.file;

import dev.ainer.module.file.file.application.FileStorageProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module configuration for the file storage slice (ADR-0040). Enabled by default; disable with
 * {@code ainer.file.enabled=false}.
 *
 * <p>Storage backend is the {@link dev.ainer.core.storage.FileStoragePort} SPI. The framework's
 * local adapter is wired by {@code LocalFileStorageAutoConfiguration} (ainer-spring); products
 * override with S3/OSS/MinIO implementations by declaring their own {@code FileStoragePort} bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = FileFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = FileFeatureMarker.class, annotationClass = Mapper.class))
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock fileClock() {
        return Clock.systemUTC();
    }
}
