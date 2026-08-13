package dev.ainer.security.token;

import java.util.Objects;

/**
 * Closed set of Ainer Foundation token profiles (ADR-0033 Greenfield §6.1).
 *
 * <p>Each profile fixes how {@code sub}, {@code actor_type}, audience, scope and an optional workspace access
 * ceiling are interpreted together with a claim-contract version. The legacy {@code tenant_id} / tenant-roles
 * profile is deliberately not part of the Greenfield baseline; any token lacking a known profile / version
 * must fail closed at resolution time.
 *
 * <p>Profiles are not freely combinable: a USER_NEUTRAL token never carries a workspace ceiling, a
 * USER_WORKSPACE token requires one, and a SERVICE token never represents a human. The wire value is carried
 * in the {@code token_profile} claim alongside {@code claim_contract_version}.
 */
public enum TokenProfile {

    USER_NEUTRAL_V1("USER_NEUTRAL_V1"),
    USER_WORKSPACE_V1("USER_WORKSPACE_V1"),
    SERVICE_V1("SERVICE_V1");

    /** Claim name carrying the profile wire value. */
    public static final String PROFILE_CLAIM = "token_profile";

    /** Claim name carrying the claim-contract version. */
    public static final String CONTRACT_VERSION_CLAIM = "claim_contract_version";

    /** Current claim-contract version of the Greenfield baseline. */
    public static final String CURRENT_CONTRACT_VERSION = "1";

    private final String claimValue;

    TokenProfile(String claimValue) {
        this.claimValue = claimValue;
    }

    /** Canonical wire value placed in the {@code token_profile} claim. */
    public String claimValue() {
        return claimValue;
    }

    /**
     * Resolve a profile from its wire value. Fail closed: blank or unknown values throw, so a resolver can
     * never silently accept an unprofiled token.
     */
    public static TokenProfile fromClaim(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        for (TokenProfile profile : values()) {
            if (profile.claimValue.equals(trimmed)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown token_profile claim: " + value);
    }
}
