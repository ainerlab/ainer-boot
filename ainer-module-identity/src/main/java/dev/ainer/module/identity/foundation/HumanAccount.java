package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Human security-account lifecycle root within one {@link IdentityAuthorityRef} (ADR-0033 Greenfield §3).
 *
 * <p>A HumanAccount is not a global person master record, not a login identifier and not a Tenant membership.
 * One natural person may legitimately hold several HumanAccounts across authorities/realms; identical emails,
 * phones or usernames never auto-merge. The account can exist with zero Workspace memberships, and disabling
 * or closing it never cascades into Workspace, content or audit deletion.
 *
 * <p>{@code securityEpoch} is a monotonic account-wide revocation version: credentials, sessions and tokens
 * issued before the current epoch are invalid. It is the Greenfield replacement for the legacy
 * {@code (tenantId, subjectId)} token-status lookup.
 */
public record HumanAccount(
        UUID accountId,
        IdentityAuthorityRef authority,
        AccountStatus status,
        long securityEpoch,
        Instant createdAt) {

    public HumanAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (securityEpoch < 0) {
            throw new IllegalArgumentException("securityEpoch must be non-negative");
        }
    }

    /**
     * The authority-qualified principal reference used by authorization, audit and resource attribution.
     */
    public HumanSubjectRef toSubjectRef() {
        return new HumanSubjectRef(authority, accountId.toString());
    }
}
