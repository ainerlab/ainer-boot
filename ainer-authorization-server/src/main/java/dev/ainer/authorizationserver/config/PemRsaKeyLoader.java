package dev.ainer.authorizationserver.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemRsaKeyLoader {

    private final ResourceLoader resourceLoader;

    PemRsaKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    RSAPrivateKey privateKey(String location) {
        byte[] bytes = pem(location, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Ainer authorization signing private key is invalid", exception);
        }
    }

    RSAPublicKey publicKey(String location) {
        byte[] bytes = pem(location, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Ainer authorization signing public key is invalid", exception);
        }
    }

    private byte[] pem(String location, String type) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Ainer authorization signing " + type.toLowerCase() + " location is required");
        }
        Resource resource = resourceLoader.getResource(location);
        try {
            String value = resource.getContentAsString(StandardCharsets.US_ASCII)
                    .replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(value);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read Ainer authorization signing key from " + location, exception);
        }
    }
}
