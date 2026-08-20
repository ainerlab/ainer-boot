package dev.ainer.spring.storage;

import dev.ainer.core.storage.FileStoragePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 本地文件系统 {@link FileStoragePort} 适配器的自动装配（ADR-0038）。
 *
 * <p>默认启用（{@code ainer.storage.local.enabled=true}）。产品可覆盖
 * {@link FileStoragePort} bean 以提供 S3/OSS/MinIO 适配器。
 */
@AutoConfiguration
@EnableConfigurationProperties(LocalFileStorageAutoConfiguration.LocalFileStorageProperties.class)
@ConditionalOnProperty(prefix = "ainer.storage.local", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalFileStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStoragePort.class)
    public FileStoragePort localFileStoragePort(LocalFileStorageProperties properties) {
        return new LocalFileStorageAdapter(properties.baseDirectory());
    }

    @ConfigurationProperties(prefix = "ainer.storage.local")
    public record LocalFileStorageProperties(
            boolean enabled,
            String baseDirectory) {

        public LocalFileStorageProperties {
            if (baseDirectory == null || baseDirectory.isBlank()) {
                baseDirectory = "./data/ainer-storage";
            }
        }
    }
}
