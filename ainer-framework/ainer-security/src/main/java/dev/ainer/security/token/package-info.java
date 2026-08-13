/**
 * Greenfield token-profile contracts (ADR-0033 Greenfield §6.1, ADR-0030 §2.2).
 *
 * <p>Typed, profile-qualified projection of a verified access token. Ainer Foundation no longer defines a
 * single tenant-bound actor; instead each verified JWT is resolved into an {@link
 * dev.ainer.security.token.AuthenticatedPrincipal} that pairs an authority-qualified {@link
 * dev.ainer.security.principal.PrincipalSubjectRef} with a closed {@link dev.ainer.security.token.TokenProfile},
 * a claim-contract version, audiences, OAuth scope ceiling and authentication assurance. The
 * {@code tenant_id} / {@code tenant roles} claims of the legacy profile are intentionally absent; a workspace
 * access ceiling is added in a later slice once {@code WorkspaceRef} exists.
 *
 * <p>Business modules consume only the typed {@code AuthenticatedPrincipal} via a {@link
 * dev.ainer.security.token.TokenProfileResolver} port; they never re-parse raw JWT claims. Unknown profile,
 * missing contract version or claim/profile mismatch must fail closed. This package is additive during the
 * Greenfield reset and does not yet wire into the Authorization Server.
 *
 * <p>Package-level {@link org.jspecify.annotations.NullMarked} declares every type, parameter and return value
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package dev.ainer.security.token;

import org.jspecify.annotations.NullMarked;
