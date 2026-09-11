package dev.ainer.authorizationserver.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SigningKeyRing} 的装载语义与失败关闭边界。
 *
 * <p>这些断言都指向同一个生产事故形态：**配置看起来生效了，实际没有**。例如 active key 少写一个
 * 私钥文件、目录里 key 文件名拼错、公钥私钥不是同一对——在运行期表现为「签发的 Token 谁也验不过」
 * 或「以为切了 key 其实还在用旧的」。因此每一条都在启动期失败关闭，而不是留给运行期。
 *
 * <p>不依赖 Spring 容器与 Docker：直接对装载器断言，真实 RSA 密钥对由 Nimbus 现场生成。
 */
class SigningKeyRingTest {

    private static final RSAKey ACTIVE_KEY = generate("ring-active");
    private static final RSAKey PREVIOUS_KEY = generate("ring-previous");

    private final PemRsaKeyLoader loader = new PemRsaKeyLoader(new DefaultResourceLoader());

    @Test
    @DisplayName("目录形态发布全部 key，但只有激活 key 携带私钥材料")
    void directoryPublishesEveryKeyButOnlyActiveKeyCanSign(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);
        writeKeyPair(directory, "ring-previous", PREVIOUS_KEY);

        SigningKeyRing ring = ring(directory, "ring-active");

        assertThat(ring.activeKeyId()).isEqualTo("ring-active");
        assertThat(ring.publishedKeyIds()).containsExactly("ring-active", "ring-previous");
        assertThat(ring.publishedJwks())
                .filteredOn(JWK::isPrivate)
                .singleElement()
                .satisfies(jwk -> assertThat(jwk.getKeyID()).isEqualTo("ring-active"));
        // 签发选择只看「谁有私钥」：与发布顺序无关，且不可能选中旧 key
        assertThat(SigningKeyRing.selectSigningKey(ring.publishedJwks()).getKeyID()).isEqualTo("ring-active");
    }

    @Test
    @DisplayName("过渡期目录里预置了下一把 key 的私钥文件，它也不会被装载进签名环")
    void privateKeyOfNonActiveKeyIsNeverLoaded(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);
        writeKeyPair(directory, "ring-previous", PREVIOUS_KEY);

        SigningKeyRing ring = ring(directory, "ring-active");

        assertThat(ring.publishedJwks())
                .filteredOn(jwk -> "ring-previous".equals(jwk.getKeyID()))
                .singleElement()
                .satisfies(jwk -> assertThat(jwk.isPrivate()).isFalse());
    }

    @Test
    @DisplayName("单文件形态等价于只剩一把 key：它既是发布 key 也是激活 key")
    void singleKeyFormIsEquivalentToOneKeyRing(@TempDir Path directory) throws IOException {
        Path privateKey = directory.resolve("private.pem");
        Path publicKey = directory.resolve("public.pem");
        writePem(privateKey, "PRIVATE KEY", privateDer(ACTIVE_KEY));
        writePem(publicKey, "PUBLIC KEY", publicDer(ACTIVE_KEY));

        SigningKeyRing ring = SigningKeyRing.load(
                new AinerAuthorizationServerProperties.SigningKey("legacy-kid",
                        privateKey.toUri().toString(), publicKey.toUri().toString()),
                null,
                loader);

        assertThat(ring.activeKeyId()).isEqualTo("legacy-kid");
        assertThat(ring.publishedKeyIds()).containsExactly("legacy-kid");
    }

    @Test
    @DisplayName("两种形态同时配置必须失败：否则「以为切到了新 key，实际还在用旧的」")
    void ambiguousConfigurationFailsClosed(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);
        AinerAuthorizationServerProperties.SigningKey single =
                new AinerAuthorizationServerProperties.SigningKey("legacy-kid", "file:/legacy/private.pem", null);

        assertThatThrownBy(() -> SigningKeyRing.load(
                single, new AinerAuthorizationServerProperties.SigningKeyRing(directory.toString(), "ring-active"),
                loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    @DisplayName("两种形态都不配置必须失败：授权服务器不能在没有签名 key 的情况下启动")
    void missingConfigurationFailsClosed() {
        assertThatThrownBy(() -> SigningKeyRing.load(null, null, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires a signing key");
    }

    @Test
    @DisplayName("激活 key 没有私钥文件时失败：环里必须恰好有一把能签发的 key")
    void activeKeyWithoutPrivateKeyFailsClosed(@TempDir Path directory) throws IOException {
        writePublicKey(directory, "ring-active", ACTIVE_KEY);

        assertThatThrownBy(() -> ring(directory, "ring-active"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no private key");
    }

    @Test
    @DisplayName("active-key-id 不在目录里时失败：配置拼错不能让服务带着旧 key 静默启动")
    void unknownActiveKeyIdFailsClosed(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);

        assertThatThrownBy(() -> ring(directory, "ring-next"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active key ring-next");
    }

    @Test
    @DisplayName("目录里出现无法识别的文件时失败：key 文件名写错不能被静默忽略")
    void unrecognizedFileFailsClosed(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);
        Files.writeString(directory.resolve("ring-next.pub.pem"), "-----BEGIN PUBLIC KEY-----\n",
                StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> ring(directory, "ring-active"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecognized file");
    }

    @Test
    @DisplayName("私钥文件缺少配对的公钥文件时失败：私钥存在却不发布，签出来的 Token 无人可验")
    void privateKeyWithoutPublicKeyFailsClosed(@TempDir Path directory) throws IOException {
        writeKeyPair(directory, "ring-active", ACTIVE_KEY);
        writePem(directory.resolve("ring-next.private.pem"), "PRIVATE KEY", privateDer(PREVIOUS_KEY));

        assertThatThrownBy(() -> ring(directory, "ring-active"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without matching");
    }

    @Test
    @DisplayName("同一 kid 的公私钥不是同一对时失败：否则签发的 Token 永远验不过")
    void mismatchedKeyPairFailsClosed(@TempDir Path directory) throws IOException {
        writePem(directory.resolve("ring-active.private.pem"), "PRIVATE KEY", privateDer(ACTIVE_KEY));
        writePem(directory.resolve("ring-active.public.pem"), "PUBLIC KEY", publicDer(PREVIOUS_KEY));

        assertThatThrownBy(() -> ring(directory, "ring-active"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same RSA key pair");
    }

    @Test
    @DisplayName("目录不存在时失败，而不是当成空环继续启动")
    void missingDirectoryFailsClosed(@TempDir Path directory) {
        assertThatThrownBy(() -> ring(directory.resolve("nope"), "ring-active"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("低于 2048 位的签名 key 被拒绝")
    void weakKeyFailsClosed(@TempDir Path directory) throws Exception {
        // Nimbus 的 RSAKeyGenerator 自身拒绝 <2048 位，因此这里用 JDK 直接造一把弱 key
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        java.security.KeyPair weak = generator.generateKeyPair();
        writePem(directory.resolve("ring-weak.private.pem"), "PRIVATE KEY", weak.getPrivate().getEncoded());
        writePem(directory.resolve("ring-weak.public.pem"), "PUBLIC KEY", weak.getPublic().getEncoded());

        assertThatThrownBy(() -> ring(directory, "ring-weak"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bits");
    }

    @Test
    @DisplayName("签发选择器在私钥数量不为 1 时失败关闭，而不是随便挑一把")
    void signingSelectionRequiresExactlyOnePrivateKey() {
        assertThatThrownBy(() -> SigningKeyRing.selectSigningKey(List.of()))
                .isInstanceOf(JwtEncodingException.class)
                .hasMessageContaining("exactly one private signing key");

        // 即使是手工构造的「两把都带私钥」集合（绕过装载期剥离），选择器也必须失败关闭
        List<JWK> twoPrivate = List.of(withPrivate(ACTIVE_KEY, "first"), withPrivate(PREVIOUS_KEY, "second"));

        assertThatThrownBy(() -> SigningKeyRing.selectSigningKey(twoPrivate))
                .isInstanceOf(JwtEncodingException.class)
                .hasMessageContaining("exactly one private signing key");
    }

    private SigningKeyRing ring(Path directory, String activeKeyId) {
        return SigningKeyRing.load(
                null,
                new AinerAuthorizationServerProperties.SigningKeyRing(directory.toString(), activeKeyId),
                loader);
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate test RSA key", exception);
        }
    }

    private static void writeKeyPair(Path directory, String keyId, RSAKey key) throws IOException {
        writePem(directory.resolve(keyId + ".private.pem"), "PRIVATE KEY", privateDer(key));
        writePublicKey(directory, keyId, key);
    }

    private static void writePublicKey(Path directory, String keyId, RSAKey key) throws IOException {
        writePem(directory.resolve(keyId + ".public.pem"), "PUBLIC KEY", publicDer(key));
    }

    private static JWK withPrivate(RSAKey key, String keyId) {
        try {
            return new RSAKey.Builder(key.toRSAPublicKey()).privateKey(key.toRSAPrivateKey())
                    .keyID(keyId).build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to build test JWK", exception);
        }
    }

    private static byte[] privateDer(RSAKey key) {
        try {
            return key.toRSAPrivateKey().getEncoded();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to export test private key", exception);
        }
    }

    private static byte[] publicDer(RSAKey key) {
        try {
            return key.toRSAPublicKey().getEncoded();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to export test public key", exception);
        }
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        Files.writeString(path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }
}
