package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.QueryAuthorizationRequest;

/**
 * Produces a typed {@link AuthorizedQueryPlan} for a collection/list query (ADR-0030 §7, S3).
 *
 * <p>This is the query-level analog of {@link dev.ainer.authorization.AuthorizationService#authorize}.
 * Instead of deciding on a single concrete resource, the planner evaluates the requester's bindings
 * and scope ceiling to produce a typed constraint {@code Q} that the product repository/search adapter
 * must apply to the database query.
 *
 * <p>Ainer's own implementation handles scope ceiling, binding aggregation and grant-path routing.
 * Product modules supply their own {@code I} (query intent) and {@code Q} (constraint) types; they
 * translate {@code Q} to parameterized SQL or search filters — Ainer never outputs SQL.
 *
 * @param <I> product-defined query-intent type (already input-validated)
 * @param <Q> product-defined typed query constraint (applied by the repository/search adapter)
 */
public interface QueryAuthorizationPlanner<I, Q> {

    AuthorizedQueryPlan<Q> plan(QueryAuthorizationRequest<I> request);
}
