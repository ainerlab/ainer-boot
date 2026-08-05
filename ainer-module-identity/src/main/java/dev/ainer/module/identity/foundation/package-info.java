/**
 * Greenfield Identity foundation domain (ADR-0033 Greenfield §3-§5, reset impact §4.1).
 *
 * <p>Replacement spine for the legacy tenant-bound {@code IdentityUser}/{@code IdentityTenant} model: a
 * {@link dev.ainer.module.identity.foundation.HumanAccount} is a human security-account lifecycle root
 * qualified by an {@link dev.ainer.security.principal.IdentityAuthorityRef}, with a 1:N binding to
 * {@link dev.ainer.module.identity.foundation.LoginIdentity} entries. These types are deliberately additive
 * during the S1.2 destructive slice: they coexist with the legacy runtime and do not yet wire into it. The
 * legacy {@code IdentityTenant}/{@code IdentityUser} remain the runtime authority until the cutover removes
 * them and points Identity services at this package.
 *
 * <p>Package-level {@link org.jspecify.annotations.NullMarked} declares every type, parameter and return
 * value non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.NullMarked;
