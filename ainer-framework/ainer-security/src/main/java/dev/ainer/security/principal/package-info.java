/**
 * Greenfield typed principal contracts (ADR-0033 Greenfield §2.6, ADR-0030 §2.2).
 *
 * <p>Authority-qualified, typed references to authenticatable principals. These are the Foundation's
 * stable security contracts: a {@link dev.ainer.security.principal.PrincipalSubjectRef} is always
 * qualified by an {@link dev.ainer.security.principal.IdentityAuthorityRef} so that identical raw
 * {@code sub} values from different issuers, realms or deployments never collide. Only Human and Service
 * are credential principals; Agent appears as a separate attribution ref (ADR-0031) and is deliberately
 * not part of {@code PrincipalSubjectRef}.
 *
 * <p>Package-level {@link org.jspecify.annotations.NullMarked} declares every type, parameter and return
 * value non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 *
 * <p>These contracts are the sole request-time principal vocabulary after the Greenfield cutover.
 */
@NullMarked
package dev.ainer.security.principal;

import org.jspecify.annotations.NullMarked;
