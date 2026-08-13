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
 * AES-GCM authenticated encryption for secret config values (ADR-0038 §3).
 *
 * <p>Produces self-contained ciphertexts: each encryption generates a fresh 12-byte IV, and the
 * output format is {@code base64(iv || ciphertext+tag)}. Decryption requires the same key.
 *
 * <p>The key is supplied by the product via configuration (e.g. a KMS-derived envelope key or a
 * base64-encoded 256-bit key). This class does NOT manage key rotation or KMS integration — those
 * are the product's responsibility. The Ainer default uses a single configured key.
 *
 * <p>Thread-safe: each encrypt/decrypt creates a new Cipher instance.
 */
public final class AesGcmEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param key raw AES key bytes (16/24/32 bytes for AES-128/192/256)
     */
    public AesGcmEncryptor(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("AES key must be 16/24/32 bytes, got " + key.length);
        }
        this.keySpec = new SecretKeySpec(key, ALGORITHM);
    }

    /**
     * Create from a base64-encoded key string.
     */
    public static AesGcmEncryptor fromBase64Key(String base64Key) {
        return new AesGcmEncryptor(Base64.getDecoder().decode(base64Key));
    }

    /**
     * Encrypt plaintext. Output format: {@code base64(iv[12] || ciphertext+tag)}.
     */
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine iv + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Decrypt a value produced by {@link #encrypt}. Returns the original plaintext.
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
