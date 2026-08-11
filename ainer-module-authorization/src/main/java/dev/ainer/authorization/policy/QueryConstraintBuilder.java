package dev.ainer.authorization.policy;

import dev.ainer.authorization.DefaultQueryAuthorizationPlanner;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import org.jspecify.annotations.Nullable;

/**
 * Product-supplied builder that translates a set of authorized {@link SubjectBinding}s into a typed
 * query constraint {@code Q} (ADR-0030 §7, S3).
 *
 * <p>Ainer's {@link DefaultQueryAuthorizationPlanner} calls this builder for each live binding that
 * grants the requested permission. The product implementation accumulates the constraints (e.g.
 * "allow workspace X", "allow resource Y", "allow all if GLOBAL") into a single {@code Q} that the
 * product repository applies to the database/search query.
 *
 * <p>If no binding grants access, the planner returns {@link AuthorizedQueryPlan.Denied} without
 * constructing an allowed query plan. The first call receives a {@code null} current constraint;
 * every implementation must return a non-null product constraint.
 *
 * @param <Q> product-defined typed query constraint
 */
@FunctionalInterface
public interface QueryConstraintBuilder<Q> {

    /**
     * Accumulate a single authorized binding into the constraint being built.
     *
     * <p>Typical translations:
     * <ul>
     *   <li>{@link Scope.Global} → no constraint (see everything the product query returns)</li>
     *   <li>{@link Scope.Workspace} → restrict to that workspace</li>
     *   <li>{@link Scope.Resource} → restrict to that specific resource</li>
     * </ul>
     *
     * @param current      accumulated constraint, or {@code null} on the first call
     * @param binding      the live binding that grants the requested permission (never REVOKED)
     * @param permission   the permission being queried (the binding's role grants it)
     * @param resourceType the resource type of the query (matches all relevant bindings)
     * @return the accumulated constraint so far, or a new constraint for the first call
     */
    Q accumulate(
            @Nullable Q current,
            SubjectBinding binding,
            PermissionCode permission,
            ResourceType resourceType);
}
