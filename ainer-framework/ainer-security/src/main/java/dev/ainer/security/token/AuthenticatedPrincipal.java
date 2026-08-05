package dev.ainer.security.token;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.PrincipalSubjectRef;
import dev.ainer.security.principal.ServiceSubjectRef;

import java.util.Objects;
import java.util.Set;

/**
 * Typed, profile-qualified projection of a verified access token (ADR-0030 §2.2, ADR-0033 Greenfield §6.1).
 *
 * <p>Replaces the tenant-bound {@code AuthenticatedActor} as the canonical request-time principal for new
 * Foundation code. It pairs an authority-qualified {@link PrincipalSubjectRef} with a closed {@link
 * TokenProfile}, claim-contract version, OAuth audience and scope ceiling, and authentication assurance. The
 * {@code credentialTenantId} of the legacy projection is intentionally absent: tenant is no longer a
 * principal attribute.
 *
 * <p>Invariants are enforced at construction: a {@code USER_*} profile requires a {@link HumanSubjectRef},
 * {@code SERVICE_V1} requires a {@link ServiceSubjectRef}. A workspace access ceiling (for
 * {@code USER_WORKSPACE_V1}) is introduced in a later slice with {@code WorkspaceRef}; until then a
 * workspace-scoped principal is still expressible via its profile and scope ceiling.
 */
public record AuthenticatedPrincipal(
        PrincipalSubjectRef principalSubjectRef,
        IdentityAuthorityRef authority,
        TokenProfile tokenProfile,
        String claimContractVersion,
        Set<String> audiences,
        Set<String> scopes,
        String assurance,
        String clientId) {

    public AuthenticatedPrincipal {
        Objects.requireNonNull(principalSubjectRef, "principalSubjectRef");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(tokenProfile, "tokenProfile");
        Objects.requireNonNull(claimContractVersion, "claimContractVersion");
        Objects.requireNonNull(audiences, "audiences");
        Objects.requireNonNull(scopes, "scopes");
        Objects.requireNonNull(assurance, "assurance");
        if (claimContractVersion.isBlank() || assurance.isBlank()) {
            throw new IllegalArgumentException("claimContractVersion and assurance must be non-blank");
        }
        requireProfileConsistency(tokenProfile, principalSubjectRef);
        audiences = Set.copyOf(audiences);
        scopes = Set.copyOf(scopes);
    }

    public boolean isHuman() {
        return principalSubjectRef instanceof HumanSubjectRef;
    }

    public boolean isService() {
        return principalSubjectRef instanceof ServiceSubjectRef;
    }

    private static void requireProfileConsistency(
            TokenProfile profile, PrincipalSubjectRef principal) {
        boolean userProfile = profile == TokenProfile.USER_NEUTRAL_V1
                || profile == TokenProfile.USER_WORKSPACE_V1;
        boolean human = principal instanceof HumanSubjectRef;
        if (userProfile != human) {
            throw new IllegalArgumentException(
                    "TokenProfile " + profile + " is inconsistent with principal kind "
                            + principal.getClass().getSimpleName());
        }
    }
}
