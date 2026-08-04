package dev.ainer.security.principal;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The interpretation scope of account / service-principal IDs (ADR-0033 Greenfield §2.2).
 *
 * <p>An {@code IdentityAuthorityRef} qualifies a stable ID so that the same raw identifier issued under
 * different trust domains is never treated as the same object. It is a value object; v1 may be resolved
 * from a trusted {@code iss} claim without a persistent aggregate.
 *
 * <p>{@code issuer} is the canonical trusted issuer (for example the OAuth {@code iss} URL). {@code realm}
 * is an optional discriminator used only when one issuer serves multiple realms, deployments or private
 * instances; absence means the issuer alone identifies the authority.
 *
 * <p>It is deliberately not a new Tenant: it owns no Workspace, member, plan, contract, organization or
 * isolation domain. A raw {@code sub}, email, phone number or bare UUID never implies an authority.
 */
public record IdentityAuthorityRef(String issuer, @Nullable String realm) {

    private static final int ISSUER_MAX_LENGTH = 256;
    private static final int REALM_MAX_LENGTH = 128;

    public IdentityAuthorityRef {
        Objects.requireNonNull(issuer, "issuer");
        String normalizedIssuer = issuer.trim();
        if (normalizedIssuer.isEmpty() || normalizedIssuer.length() > ISSUER_MAX_LENGTH) {
            throw new IllegalArgumentException("issuer must be a non-blank string of at most "
                    + ISSUER_MAX_LENGTH + " characters");
        }
        issuer = normalizedIssuer;
        if (realm != null) {
            String normalizedRealm = realm.trim();
            if (normalizedRealm.isEmpty() || normalizedRealm.length() > REALM_MAX_LENGTH) {
                throw new IllegalArgumentException("realm must be a non-blank string of at most "
                        + REALM_MAX_LENGTH + " characters when present");
            }
            realm = normalizedRealm;
        }
    }

    /**
     * Convenience constructor for the common single-realm case.
     */
    public IdentityAuthorityRef(String issuer) {
        this(issuer, null);
    }

    public boolean hasRealm() {
        return realm != null;
    }
}
