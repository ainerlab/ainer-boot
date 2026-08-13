package dev.ainer.security.principal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reference to a human security account within an {@link IdentityAuthorityRef} (ADR-0033 Greenfield §3).
 *
 * <p>{@code accountId} is a HumanAccount ID, not a global person master record, not a login identifier
 * and not a Tenant membership. One natural person may legitimately hold several HumanAccounts across
 * different authorities, realms or deployments; identical emails, phones or usernames never auto-merge.
 */
public record HumanSubjectRef(IdentityAuthorityRef authority, String accountId)
        implements PrincipalSubjectRef {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public HumanSubjectRef {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(accountId, "accountId");
        String normalized = accountId.trim();
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("accountId is invalid");
        }
        accountId = normalized;
    }

    @Override
    public String subjectId() {
        return accountId;
    }
}
