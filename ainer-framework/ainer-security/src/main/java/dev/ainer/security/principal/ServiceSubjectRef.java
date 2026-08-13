package dev.ainer.security.principal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reference to a stable non-human service principal within an {@link IdentityAuthorityRef}
 * (ADR-0033 Greenfield §2.6).
 *
 * <p>A {@code ServicePrincipal} is the stable identity of a non-human caller; an OAuth {@code client_id}
 * is a rotatable credential/client identifier bound to it, not the principal itself. Client rotation must
 * not change the audit identity, and a Service can never hold a human WorkspaceMembership or a governance
 * OWNER role.
 */
public record ServiceSubjectRef(IdentityAuthorityRef authority, String servicePrincipalId)
        implements PrincipalSubjectRef {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public ServiceSubjectRef {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(servicePrincipalId, "servicePrincipalId");
        String normalized = servicePrincipalId.trim();
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("servicePrincipalId is invalid");
        }
        servicePrincipalId = normalized;
    }

    @Override
    public String subjectId() {
        return servicePrincipalId;
    }
}
