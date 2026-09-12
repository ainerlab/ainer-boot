package dev.ainer.security.autoconfigure;

import java.net.URI;

/**
 * 资源服务器验签公钥的来源（JWKS 信任锚）在启动期解析出的结果。
 *
 * <p>刻意做成一个小而不可变的值对象而不是「校验一下就丢」的副作用：它让「信任锚到底指向哪里」
 * 在容器里可被断言、可被日志打印，也让启动期失败关闭有一个明确的落点。
 *
 * @param jwkSetUri 已校验的 JWK Set 地址；{@code null} 表示未显式配置（走 issuer-uri discovery）
 * @param issuerUri 配置的 issuer（用于 issuer claim 校验）；可能为 {@code null}
 */
public record AinerJwkSetTrustAnchor(URI jwkSetUri, String issuerUri) {
}
