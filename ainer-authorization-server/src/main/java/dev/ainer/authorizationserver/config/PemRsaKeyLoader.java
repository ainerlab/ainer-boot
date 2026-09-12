package dev.ainer.authorizationserver.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 形态 RSA 密钥（PKCS#8 私钥 / X.509 SubjectPublicKeyInfo 公钥）的唯一读取入口。
 *
 * <p>两种来源：{@code ResourceLoader} 位置（历史单文件形态，支持 {@code file:} / {@code classpath:}）
 * 与密钥环目录中的具体文件（{@link SigningKeyRing}）。所有失败都抛出带来源路径的
 * {@link IllegalStateException}，让授权服务器在启动期失败关闭，而不是运行期才发现 key 不可用。
 */
final class PemRsaKeyLoader {

    private final ResourceLoader resourceLoader;

    PemRsaKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    RSAPrivateKey privateKey(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Ainer authorization signing private key location is required");
        }
        Resource resource = resourceLoader.getResource(location);
        try {
            return privateKey(resource.getContentAsString(StandardCharsets.US_ASCII), location);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Ainer authorization signing key from " + location, exception);
        }
    }

    RSAPublicKey publicKey(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Ainer authorization signing public key location is required");
        }
        Resource resource = resourceLoader.getResource(location);
        try {
            return publicKey(resource.getContentAsString(StandardCharsets.US_ASCII), location);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Ainer authorization signing key from " + location, exception);
        }
    }

    RSAPrivateKey privateKey(Path path) {
        return privateKey(read(path), path.toString());
    }

    RSAPublicKey publicKey(Path path) {
        return publicKey(read(path), path.toString());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Ainer authorization signing key from " + path, exception);
        }
    }

    private static RSAPrivateKey privateKey(String pem, String source) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY", source)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ainer authorization signing private key is invalid: " + source, exception);
        }
    }

    private static RSAPublicKey publicKey(String pem, String source) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decode(pem, "PUBLIC KEY", source)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ainer authorization signing public key is invalid: " + source, exception);
        }
    }

    private static byte[] decode(String pem, String type, String source) {
        String value = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        if (value.isEmpty()) {
            throw new IllegalStateException("Ainer authorization signing key file is empty: " + source);
        }
        return Base64.getDecoder().decode(value);
    }
}
