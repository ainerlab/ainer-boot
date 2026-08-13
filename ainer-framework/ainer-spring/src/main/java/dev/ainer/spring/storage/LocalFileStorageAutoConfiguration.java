package dev.ainer.spring.storage;

import dev.ainer.core.storage.FileStoragePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the local-filesystem {@link FileStoragePort} adapter (ADR-0038).
 *
 * <p>Enabled by default ({@code ainer.storage.local.enabled=true}). Products override the
 * {@link FileStoragePort} bean to supply S3/OSS/MinIO adapters.
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
