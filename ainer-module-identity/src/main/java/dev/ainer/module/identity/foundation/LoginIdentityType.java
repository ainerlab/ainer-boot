package dev.ainer.module.identity.foundation;

/**
 * {@link LoginIdentity} 所代表的认证绑定种类（ADR-0033 Greenfield §4）。
 *
 * <p>每种类型有各自的标识符规范化、验证仪式、provider 权威与吊销语义。不同类型、
 * provider 或权威下的相同规范化标识符绝不隐含同一个 {@link HumanAccount}。
 */
public enum LoginIdentityType {

    USERNAME,
    EMAIL,
    PHONE,
    WECHAT,
    OIDC,
    PASSKEY
}
