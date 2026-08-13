/**
 * Infrastructure adapters for the authorization persistence slice (ADR-0030 S1).
 * Implements the application-layer repository ports using MyBatis + PostgreSQL 18.
 * Package-level {@link org.jspecify.annotations.NullMarked} declares every type, parameter and return
 * value non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package dev.ainer.authorization.infrastructure;

import org.jspecify.annotations.NullMarked;
