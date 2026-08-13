package dev.ainer.security.principal;

/**
 * Authority-qualified reference to a credential principal (ADR-0033 Greenfield §2.6, ADR-0030 §2.2).
 *
 * <p>Only Human and Service are credential principals. The type is sealed so that authorization bindings,
 * JWT parsers, credential/effective principal APIs and {@code SubjectBinding} targets can exhaustively
 * accept {@code PrincipalSubjectRef} without ever silently admitting an Agent or anonymous value.
 *
 * <p>Non-equivalence is structural: a Human and a Service are never the same principal even if their raw
 * IDs coincide, and the same ID under different {@link IdentityAuthorityRef authorities} is never equal.
 */
public sealed interface PrincipalSubjectRef
        permits HumanSubjectRef, ServiceSubjectRef {

    /**
     * The authority that qualifies this principal's ID.
     */
    IdentityAuthorityRef authority();

    /**
     * The stable identifier of this principal within its authority (account ID for Human, service
     * principal ID for Service). Uniform accessor for attribution and audit; never the raw JWT claim set.
     */
    String subjectId();
}
