package dev.ainer.security.actor;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Trusted identity projected from a successfully authenticated credential.
 */
public record AuthenticatedActor(
        String subjectId,
        String tenantId,
        Set<String> authorities) {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public AuthenticatedActor {
        subjectId = requireIdentifier(subjectId, "subjectId");
        tenantId = requireIdentifier(tenantId, "tenantId");
        authorities = Set.copyOf(Objects.requireNonNull(authorities, "authorities"));
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(Objects.requireNonNull(authority, "authority"));
    }

    public void requireAuthority(String authority) {
        if (!hasAuthority(authority)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
