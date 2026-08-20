package dev.ainer.module.config.config.application;

/**
 * secret 配置值加解密的 SPI（ADR-0038 §3）。
 * 默认实现使用 AES-GCM（见 {@link dev.ainer.core.crypto.AesGcmEncryptor}）。
 * 产品可用 KMS/密钥信封实现覆盖。
 */
public interface ConfigEncryptionPort {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
