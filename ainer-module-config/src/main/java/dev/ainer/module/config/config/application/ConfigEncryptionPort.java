package dev.ainer.module.config.config.application;

/**
 * SPI for encrypting/decrypting secret config values (ADR-0038 §3).
 * Default implementation uses AES-GCM (see {@link dev.ainer.core.crypto.AesGcmEncryptor}).
 * Products override with KMS/envelope key implementations.
 */
public interface ConfigEncryptionPort {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
