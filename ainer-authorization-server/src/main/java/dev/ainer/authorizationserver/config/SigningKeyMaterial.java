package dev.ainer.authorizationserver.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 签名密钥环中的一把密钥：稳定的 {@code kid} + 已加载的密钥材料。
 *
 * <p>{@code privateKey} 为 {@code null} 表示这把 key 只能验证、不能签发——这正是轮换过渡期里
 * 旧 key 的形态：它仍在 JWK Set 中发布（在途 Token 继续验签通过），但已经不可能再被用来签发
 * 新 Token。把这个区别放在类型里而不是靠调用方自觉，是为了让「用哪把 key 签发」只有
 * {@link SigningKeyRing#activeKeyId()} 一个来源。
 */
record SigningKeyMaterial(String keyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {

    /** 是否携带私钥材料（只有当前激活 key 允许为 {@code true}）。 */
    boolean signingCapable() {
        return privateKey != null;
    }
}
