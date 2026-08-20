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
 * 动态配置切片的模块配置（ADR-0038）。默认启用，可通过 {@code ainer.config.enabled=false}
 * 关闭。
 *
 * <p>secret 加密默认使用 {@link AesGcmEncryptor}，密钥来自
 * {@code ainer.config.encryption-key}（base64 编码的 AES-256 密钥）。产品可用 KMS/密钥信封
 * 实现覆盖 {@link ConfigEncryptionPort}。
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
     * secret 配置值的默认 AES-GCM 加密端口。密钥必须通过
     * {@code ainer.config.encryption-key} 提供（base64 编码，AES-256 需 32 字节）。
     * 未配置密钥时会生成临时一次性密钥（不适合生产——
     * 用它加密的 secret 在重启后将无法解密）。
     */
    @Bean
    @ConditionalOnMissingBean(ConfigEncryptionPort.class)
    public ConfigEncryptionPort defaultConfigEncryptionPort(
            @Value("${ainer.config.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            // 仅开发用的一次性密钥——重启后不保留
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
