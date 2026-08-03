package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Typed authorization challenge (ADR-0030 §6.3). A CHALLENGE outcome means the action must not execute
 * until the challenge is satisfied and the decision re-evaluated with the new authentication result.
 * S0 only implements {@link AuthenticationChallenge}; transaction confirmation and human approval are
 * reserved type boundaries, not active values.
 */
public sealed interface Challenge permits Challenge.AuthenticationChallenge {

    /** Requires the subject to complete recent strong authentication (RFC 9470 Step-up). */
    record AuthenticationChallenge(AuthorizationContext.Assurance requiredAssurance) implements Challenge {

        public AuthenticationChallenge {
            Objects.requireNonNull(requiredAssurance, "requiredAssurance");
        }
    }
}
