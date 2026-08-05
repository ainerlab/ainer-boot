/**
 * Authorization policy ports consumed by the decision evaluator (ADR-0030 §5). Implementations are provided
 * by product/domain modules and by the persistence/management slices; the decision core remains Spring-free.
 * Package-level {@link org.jspecify.annotations.NullMarked}.
 */
@NullMarked
package dev.ainer.authorization.policy;

import org.jspecify.annotations.NullMarked;
