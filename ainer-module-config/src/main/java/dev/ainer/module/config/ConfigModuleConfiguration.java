package dev.ainer.module.config;

import dev.ainer.core.crypto.AesGcmEncryptor;
import dev.ainer.module.config.config.application.ConfigEncryptionPort;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module configuration for the dynamic config slice (ADR-0038). Enabled by default; disable with
 * {@code ainer.config.enabled=false}.
 *
 * <p>Secret encryption uses {@link AesGcmEncryptor} by default, keyed by
 * {@code ainer.config.encryption-key} (base64-encoded AES-256 key). Products override
 * {@link ConfigEncryptionPort} with KMS/envelope implementations.
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

    /**
     * Default AES-GCM encryption port for secret config values. The key must be supplied via
     * {@code ainer.config.encryption-key} (base64-encoded, 32 bytes for AES-256).
     * If no key is configured, a temporary ephemeral key is generated (NOT suitable for production —
     * secrets encrypted with it become undecryptable after restart).
     */
    @Bean
    @ConditionalOnMissingBean(ConfigEncryptionPort.class)
    public ConfigEncryptionPort defaultConfigEncryptionPort(
            @Value("${ainer.config.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            // Ephemeral key for development only — not persistent across restarts
            byte[] ephemeral = new byte[32];
            new java.security.SecureRandom().nextBytes(ephemeral);
            return new AesGcmConfigEncryptionPort(new AesGcmEncryptor(ephemeral));
        }
        return new AesGcmConfigEncryptionPort(AesGcmEncryptor.fromBase64Key(base64Key));
    }

    private static final class AesGcmConfigEncryptionPort implements ConfigEncryptionPort {
        private final AesGcmEncryptor encryptor;

        AesGcmConfigEncryptionPort(AesGcmEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        @Override
        public String encrypt(String plaintext) {
            return encryptor.encrypt(plaintext);
        }

        @Override
        public String decrypt(String ciphertext) {
            return encryptor.decrypt(ciphertext);
        }
    }
}
