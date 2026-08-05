/**
 * Ainer authorization decision core (ADR-0030). The {@link dev.ainer.authorization.AuthorizationService}
 * is Spring-free and consumes domain contracts plus policy ports; persistence, Spring and management
 * adapters attach at explicit boundaries in later slices. Package-level
 * {@link org.jspecify.annotations.NullMarked}.
 */
@NullMarked
package dev.ainer.authorization;

import org.jspecify.annotations.NullMarked;
