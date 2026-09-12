package dev.ainer.authorizationserver.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 签名密钥环：授权服务器「发布哪些 key」与「用哪把 key 签发」的唯一来源。
 *
 * <p>语义（三条，全部由本类与 {@code AinerAuthorizationServerConfiguration} 强制，不是文档承诺）：
 *
 * <ol>
 *   <li><b>发布</b>：环里的每一把 key 都进入 {@code /oauth2/jwks} 发布的 JWK Set，授权服务器自身的
 *       {@code JwtDecoder} 同样接受其中任意一把的公钥；</li>
 *   <li><b>签发</b>：只有 {@link #activeKeyId()} 那把 key 携带私钥材料，其余 key 在装载阶段就被剥成
 *       纯公钥，因此「误用旧 key 签发」在类型与数据上都不可达，不只是配置约定；</li>
 *   <li><b>移除</b>：key 不在环里就不再发布，也就立刻不再被接受（未知 {@code kid} 失败关闭）。
 *       环在应用启动时装载一次，改目录必须重启授权服务器——不提供热加载，避免「目录半写状态被
 *       读走」这类静默降级。</li>
 * </ol>
 *
 * <p>两种装载形态（互斥，同时配置即启动失败，避免「以为换了 key 其实没换」）：
 *
 * <ul>
 *   <li>目录形态 {@code ainer.security.authorization-server.signing-key-ring}：目录内每个
 *       {@code <kid>.public.pem} 是一把已发布 key，{@code <kid>.private.pem} 只允许属于当前激活
 *       key；目录里出现无法识别的文件即失败关闭（防止命名写错导致 key 静默未发布）；</li>
 *   <li>单文件形态 {@code ainer.security.authorization-server.signing-key}：历史兼容，等价于
 *       「只剩一把 key、它就是激活 key」，轮换时改用目录形态。</li>
 * </ul>
 */
final class SigningKeyRing {

    /** {@code <kid>.public.pem} / {@code <kid>.private.pem}：kid 必须文件名安全。 */
    private static final Pattern KEY_FILE = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]{0,63})\\.(public|private)\\.pem$");

    /** RSA 模数下限（JWS RS256 的最小可接受强度；低于此值已经不该再用于签发）。 */
    private static final int MINIMUM_RSA_MODULUS_BITS = 2048;

    private final String activeKeyId;
    private final List<SigningKeyMaterial> publishedKeys;
    private final JWKSource<SecurityContext> jwkSource;

    private SigningKeyRing(String activeKeyId, List<SigningKeyMaterial> publishedKeys) {
        this.activeKeyId = activeKeyId;
        this.publishedKeys = List.copyOf(publishedKeys);
        this.jwkSource = new ImmutableJWKSet<>(new JWKSet(publishedKeys.stream()
                .map(SigningKeyRing::toJwk)
                .toList()));
    }

    /** 当前用于签发的 key id（Token header 的 {@code kid} 就是它）。 */
    String activeKeyId() {
        return activeKeyId;
    }

    /** 已发布 key 的 id，激活 key 排在最前；顺序稳定，便于跑观测与断言。 */
    List<String> publishedKeyIds() {
        return publishedKeys.stream().map(SigningKeyMaterial::keyId).toList();
    }

    /** 已发布 key 的 JWK 视图（只有激活 key 带私钥材料，其余为纯公钥）。 */
    List<JWK> publishedJwks() {
        return publishedKeys.stream().map(SigningKeyRing::toJwk).toList();
    }

    /**
     * 供 {@code NimbusJwtEncoder} 与授权服务器自身使用的 JWK 源。
     *
     * <p>选中的 key 集合就是环里全部已发布 key——授权服务器签发的 Token 用它自己的公钥集验证，
     * 与资源服务器从 {@code /oauth2/jwks} 拿到的是同一份。
     */
    JWKSource<SecurityContext> jwkSource() {
        return jwkSource;
    }

    /**
     * 从已发布的 JWK 里选择用于签发的唯一一把：带私钥材料的那把。
     *
     * <p>{@code NimbusJwtEncoder} 默认在「同一算法命中多把 key」时直接抛错，因此多 key 环必须显式
     * 给出选择策略；这里选「唯一带私钥的 key」而不是「列表第一个」，让选择结果与顺序无关，且当环里
     * 出现第二把带私钥的 key 时失败关闭而不是静默挑一把。
     */
    static JWK selectSigningKey(List<JWK> jwks) {
        List<JWK> signingCapable = jwks.stream().filter(JWK::isPrivate).toList();
        if (signingCapable.size() != 1) {
            throw new JwtEncodingException(
                    "Ainer signing key ring must expose exactly one private signing key but found "
                            + signingCapable.size());
        }
        return signingCapable.getFirst();
    }

    /**
     * 按配置装载密钥环。两种形态互斥：都配置或都不配置都失败关闭。
     */
    static SigningKeyRing load(
            AinerAuthorizationServerProperties.SigningKey singleKey,
            AinerAuthorizationServerProperties.SigningKeyRing keyRing,
            PemRsaKeyLoader loader) {
        boolean singleConfigured = singleKey != null && (StringUtils.hasText(singleKey.getKeyId())
                || StringUtils.hasText(singleKey.getPrivateKeyLocation())
                || StringUtils.hasText(singleKey.getPublicKeyLocation()));
        boolean ringConfigured = keyRing != null && (StringUtils.hasText(keyRing.getDirectory())
                || StringUtils.hasText(keyRing.getActiveKeyId()));
        if (singleConfigured && ringConfigured) {
            throw new IllegalStateException("Ainer authorization signing keys are ambiguous: configure either "
                    + "ainer.security.authorization-server.signing-key or "
                    + "ainer.security.authorization-server.signing-key-ring, never both");
        }
        if (ringConfigured) {
            return fromDirectory(keyRing.getDirectory(), keyRing.getActiveKeyId(), loader);
        }
        if (singleConfigured) {
            return fromSingleKey(singleKey, loader);
        }
        throw new IllegalStateException("Ainer authorization server requires a signing key: configure "
                + "ainer.security.authorization-server.signing-key-ring.directory (rotation-ready) or the "
                + "legacy single-key ainer.security.authorization-server.signing-key.*");
    }

    /** 历史单文件形态：一把 key，既是发布 key 也是激活 key。 */
    private static SigningKeyRing fromSingleKey(
            AinerAuthorizationServerProperties.SigningKey signingKey, PemRsaKeyLoader loader) {
        if (!StringUtils.hasText(signingKey.getKeyId())) {
            throw new IllegalStateException("Ainer authorization signing key id is required");
        }
        if (!StringUtils.hasText(signingKey.getPrivateKeyLocation())) {
            throw new IllegalStateException("Ainer authorization signing private key location is required");
        }
        if (!StringUtils.hasText(signingKey.getPublicKeyLocation())) {
            throw new IllegalStateException("Ainer authorization signing public key location is required");
        }
        String keyId = signingKey.getKeyId().trim();
        RSAPrivateKey privateKey = loader.privateKey(signingKey.getPrivateKeyLocation());
        RSAPublicKey publicKey = loader.publicKey(signingKey.getPublicKeyLocation());
        return of(keyId, List.of(new SigningKeyMaterial(keyId, publicKey, privateKey)));
    }

    /**
     * 目录形态：{@code <kid>.public.pem} 为已发布 key，{@code <kid>.private.pem} 只允许属于激活 key。
     */
    private static SigningKeyRing fromDirectory(String location, String activeKeyId, PemRsaKeyLoader loader) {
        if (!StringUtils.hasText(activeKeyId)) {
            throw new IllegalStateException("Ainer authorization signing-key-ring.active-key-id is required: "
                    + "the active key id names which published key signs new tokens");
        }
        Path directory = resolveDirectory(location);
        Map<String, Path> publicKeyFiles = new LinkedHashMap<>();
        Map<String, Path> privateKeyFiles = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                if (Files.isDirectory(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.toUpperCase(Locale.ROOT).startsWith("README")) {
                    continue;
                }
                Matcher matcher = KEY_FILE.matcher(name);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Unrecognized file in Ainer signing key directory "
                            + directory + ": " + name + " (expected <kid>.public.pem or <kid>.private.pem)");
                }
                Map<String, Path> target = "public".equals(matcher.group(2)) ? publicKeyFiles : privateKeyFiles;
                target.put(matcher.group(1), entry);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Cannot list Ainer signing key directory " + directory, exception);
        }
        if (publicKeyFiles.isEmpty()) {
            throw new IllegalStateException("Ainer signing key directory " + directory
                    + " contains no published key (<kid>.public.pem)");
        }
        String active = activeKeyId.trim();
        privateKeyFiles.keySet().forEach(keyId -> {
            if (!publicKeyFiles.containsKey(keyId)) {
                throw new IllegalStateException("Ainer signing key directory " + directory + " has private key "
                        + keyId + ".private.pem without matching " + keyId + ".public.pem");
            }
        });
        if (!publicKeyFiles.containsKey(active)) {
            throw new IllegalStateException("Ainer signing key ring active key " + active
                    + " has no published public key in " + directory + " (published: "
                    + String.join(", ", publicKeyFiles.keySet()) + ")");
        }
        if (!privateKeyFiles.containsKey(active)) {
            throw new IllegalStateException("Ainer signing key ring active key " + active
                    + " has no private key " + active + ".private.pem in " + directory
                    + ": the active key is the only one allowed to sign");
        }
        List<SigningKeyMaterial> keys = new ArrayList<>();
        keys.add(new SigningKeyMaterial(active,
                loader.publicKey(publicKeyFiles.get(active)),
                loader.privateKey(privateKeyFiles.get(active))));
        publicKeyFiles.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(active))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> keys.add(new SigningKeyMaterial(
                        entry.getKey(), loader.publicKey(entry.getValue()), null)));
        return of(active, keys);
    }

    private static Path resolveDirectory(String location) {
        String trimmed = location.trim();
        Path directory = trimmed.startsWith("file:")
                ? Path.of(trimmed.substring("file:".length()))
                : Path.of(trimmed);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Ainer signing key directory does not exist or is not a directory: "
                    + directory);
        }
        return directory;
    }

    /** 环的不变量校验：签发能力唯一、kid 唯一、公私钥成对、强度不低于下限。 */
    private static SigningKeyRing of(String activeKeyId, List<SigningKeyMaterial> keys) {
        Map<String, SigningKeyMaterial> byKeyId = new LinkedHashMap<>();
        for (SigningKeyMaterial key : keys) {
            if (byKeyId.put(key.keyId(), key) != null) {
                throw new IllegalStateException("Duplicate kid in Ainer signing key ring: " + key.keyId());
            }
        }
        SigningKeyMaterial active = byKeyId.get(activeKeyId);
        if (active == null) {
            throw new IllegalStateException("Ainer signing key ring active key " + activeKeyId
                    + " is not published (published: " + String.join(", ", byKeyId.keySet()) + ")");
        }
        if (!active.signingCapable()) {
            throw new IllegalStateException("Ainer signing key ring active key " + activeKeyId
                    + " has no private key material and cannot sign");
        }
        for (SigningKeyMaterial key : keys) {
            requireStrongRsa(key.keyId(), key.publicKey().getModulus(), "public");
            if (key.signingCapable()) {
                if (!key.keyId().equals(activeKeyId)) {
                    throw new IllegalStateException("Ainer signing key ring key " + key.keyId()
                            + " carries private material but the active key is " + activeKeyId
                            + ": only the active key may sign");
                }
                requireStrongRsa(key.keyId(), key.privateKey().getModulus(), "private");
                if (!key.privateKey().getModulus().equals(key.publicKey().getModulus())) {
                    throw new IllegalStateException("Ainer signing key " + key.keyId()
                            + " public/private PEM do not belong to the same RSA key pair: tokens signed with it "
                            + "would never verify against the published public key");
                }
            }
        }
        List<SigningKeyMaterial> ordered = new ArrayList<>();
        ordered.add(active);
        keys.stream().filter(key -> !key.keyId().equals(activeKeyId)).forEach(ordered::add);
        return new SigningKeyRing(activeKeyId, ordered);
    }

    private static void requireStrongRsa(String keyId, BigInteger modulus, String part) {
        if (modulus.bitLength() < MINIMUM_RSA_MODULUS_BITS) {
            throw new IllegalStateException("Ainer signing key " + keyId + " " + part + " key is only "
                    + modulus.bitLength() + " bits; at least " + MINIMUM_RSA_MODULUS_BITS + " bits are required");
        }
    }

    private static JWK toJwk(SigningKeyMaterial key) {
        RSAKey.Builder builder = new RSAKey.Builder(key.publicKey()).keyID(key.keyId());
        if (key.signingCapable()) {
            builder.privateKey(key.privateKey());
        }
        return builder.build();
    }
}
