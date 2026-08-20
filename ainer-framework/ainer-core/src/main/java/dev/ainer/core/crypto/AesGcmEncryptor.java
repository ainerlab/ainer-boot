package dev.ainer.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * 面向机密配置值的 AES-GCM 认证加密（ADR-0038 §3）。
 *
 * <p>产出自包含密文：每次加密生成全新的 12 字节 IV，输出格式为
 * {@code base64(iv || ciphertext+tag)}。解密必须使用同一把密钥。
 *
 * <p>密钥由产品通过配置提供（例如 KMS 派生的信封密钥，或 base64 编码的 256 位密钥）。
 * 本类不负责密钥轮换或 KMS 集成——那是产品的职责。Ainer 默认只使用单一配置密钥。
 *
 * <p>线程安全：每次加密/解密都创建新的 Cipher 实例。
 */
public final class AesGcmEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param key 原始 AES 密钥字节（AES-128/192/256 对应 16/24/32 字节）
     */
    public AesGcmEncryptor(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("AES key must be 16/24/32 bytes, got " + key.length);
        }
        this.keySpec = new SecretKeySpec(key, ALGORITHM);
    }

    /**
     * 从 base64 编码的密钥字符串创建实例。
     */
    public static AesGcmEncryptor fromBase64Key(String base64Key) {
        return new AesGcmEncryptor(Base64.getDecoder().decode(base64Key));
    }

    /**
     * 加密明文。输出格式：{@code base64(iv[12] || ciphertext+tag)}。
     */
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 iv + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 解密由 {@link #encrypt} 产出的值，返回原始明文。
     */
    public String decrypt(String encryptedValue) {
        Objects.requireNonNull(encryptedValue, "encryptedValue");
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }
}
