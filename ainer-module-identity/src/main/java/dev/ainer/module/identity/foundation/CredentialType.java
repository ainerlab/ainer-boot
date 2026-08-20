package dev.ainer.module.identity.foundation;

/**
 * 附着在 {@link HumanAccount} 上的凭证材料种类（ADR-0033 Greenfield §4，执行计划 缺口 A）。
 *
 * <p>与 {@link LoginIdentityType} 正交：LoginIdentity 回答"哪些标识符可以认证这个账号"；
 * 该类型的 {@link Credential} 存储证明该标识符的材料——密码哈希、WebAuthn 公钥引用或
 * OIDC subject。一个账号每种类型至多携带一份 ACTIVE 凭证；轮换某类型会取代旧的
 * ACTIVE 材料。
 */
public enum CredentialType {

    PASSWORD,
    WEBAUTHN_PUBLIC_KEY,
    OIDC_SUBJECT
}