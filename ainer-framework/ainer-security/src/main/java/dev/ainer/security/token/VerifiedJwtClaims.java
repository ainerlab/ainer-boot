package dev.ainer.security.token;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Minimal, already-verified JWT claim surface presented to a {@link TokenProfileResolver}.
 *
 * <p>This is the input contract only: signature, issuer and audience trust are established before this value
 * is built (by the Authorization Server / Resource Server security chain). The resolver consumes these typed
 * fields plus a read-only view of raw claims to project a {@link AuthenticatedPrincipal}; business code never
 * receives this type directly.
 */
public record VerifiedJwtClaims(
        String issuer,
        String subject,
        Set<String> audiences,
        Instant expiresAt,
        Map<String, Object> claims) {

    public VerifiedJwtClaims {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(audiences, "audiences");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(claims, "claims");
        if (issuer.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("issuer and subject must be non-blank");
        }
        audiences = Set.copyOf(audiences);
        claims = Map.copyOf(claims);
    }

    /**
     * Read a raw claim as a given type, or {@code null} when absent. Callers must not rely on this for
     * security-relevant decisions; the resolver is the only authority that interprets claims.
     */
    public @Nullable Object claim(String name) {
        return claims.get(name);
    }
}
