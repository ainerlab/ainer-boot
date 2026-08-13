/**
 * Greenfield Identity foundation domain (ADR-0033 Greenfield §3-§5, reset impact §4.1).
 *
 * <p>A {@link dev.ainer.module.identity.foundation.HumanAccount} is a human security-account lifecycle root
 * qualified by an {@link dev.ainer.security.principal.IdentityAuthorityRef}, with a 1:N binding to
 * {@link dev.ainer.module.identity.foundation.LoginIdentity} entries. Account, credential, profile and
 * service-principal types are the sole Identity foundation runtime model.
 *
 * <p>Package-level {@link org.jspecify.annotations.NullMarked} declares every type, parameter and return
 * value non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.NullMarked;
