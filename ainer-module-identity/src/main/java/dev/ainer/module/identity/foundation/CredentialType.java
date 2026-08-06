package dev.ainer.module.identity.foundation;

/**
 * Kind of credential material attached to a {@link HumanAccount} (ADR-0033 Greenfield §4, execution plan
 * 缺口 A).
 *
 * <p>Orthogonal to {@link LoginIdentityType}: a LoginIdentity answers "which identifiers authenticate this
 * account"; a {@link Credential} of this type stores the material that proves the identifier — a password
 * hash, a WebAuthn public key reference, or an OIDC subject. One account carries at most one ACTIVE
 * credential per type; rotating a type supersedes the old ACTIVE material.
 */
public enum CredentialType {

    PASSWORD,
    WEBAUTHN_PUBLIC_KEY,
    OIDC_SUBJECT
}