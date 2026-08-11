package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;

/**
 * Product-supplied builder that translates a set of authorized {@link SubjectBinding}s into a typed
 * query constraint {@code Q} (ADR-0030 §7, S3).
 *
 * <p>Ainer's {@link DefaultQueryAuthorizationPlanner} calls this builder for each live binding that
 * grants the requested permission. The product implementation accumulates the constraints (e.g.
 * "allow workspace X", "allow resource Y", "allow all if GLOBAL") into a single {@code Q} that the
 * product repository applies to the database/search query.
 *
 * <p>If no binding grants access (the builder produces no constraint), the planner returns
 * {@link AuthorizedQueryPlan.Denied}. This means the builder's {@code result()} is only called when
 * at least one binding contributed.
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
     * @param binding     the live binding that grants the requested permission (never REVOKED)
     * @param permission  the permission being queried (the binding's role grants it)
     * @param resourceType the resource type of the query (matches all relevant bindings)
     * @return the accumulated constraint so far, or a new constraint for the first call
     */
    Q accumulate(Q current, SubjectBinding binding, PermissionCode permission, ResourceType resourceType);
}
