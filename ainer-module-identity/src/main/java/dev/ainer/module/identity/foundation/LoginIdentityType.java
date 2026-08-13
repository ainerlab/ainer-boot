package dev.ainer.module.identity.foundation;

/**
 * Kind of authentication binding a {@link LoginIdentity} represents (ADR-0033 Greenfield §4).
 *
 * <p>Each type carries its own identifier normalization, verification ceremony, provider authority and
 * revocation semantics. Equal normalized identifiers across different types, providers or authorities never
 * imply the same {@link HumanAccount}.
 */
public enum LoginIdentityType {

    USERNAME,
    EMAIL,
    PHONE,
    WECHAT,
    OIDC,
    PASSKEY
}
