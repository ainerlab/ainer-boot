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
 * 文件存储切片的模块配置（ADR-0040）。默认启用，可通过
 * {@code ainer.file.enabled=false} 关闭。
 *
 * <p>存储后端是 {@link dev.ainer.core.storage.FileStoragePort} SPI。框架的本地适配器由
 * {@code LocalFileStorageAutoConfiguration}（ainer-spring）装配；产品可通过声明自己的
 * {@code FileStoragePort} Bean 覆盖为 S3/OSS/MinIO 实现。
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
