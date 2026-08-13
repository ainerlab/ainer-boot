package dev.ainer.authorization.domain;

import dev.ainer.authorization.AuthorizationReasonCodes;

import java.util.List;
import java.util.Objects;

/**
 * Result of a collection-query authorization (ADR-0030 §7, S3). The authorization engine either
 * produces a typed query constraint {@code Q} that the product repository must apply to exclude
 * unauthorized rows, or denies the entire query.
 *
 * <p>{@code Q} is product-defined — Ainer does not know product table names, column names or search
 * DSL. The product adapter translates {@code Q} to parameterized PostgreSQL or search-engine filter
 * conditions. Unpacking {@code Allowed} and ignoring {@code Denied} is a contract violation.
 *
 * @param <Q> product-defined typed query constraint
 */
public sealed interface AuthorizedQueryPlan<Q> {

    /**
     * The query is authorized. The product adapter must apply {@code constraint} to the database/search
     * query so unauthorized rows are excluded at the data layer — not loaded into JVM and filtered later.
     *
     * @param constraint     product-defined typed constraint (e.g. {@code allowedWorkspaceIds, publicOnly})
     * @param obligations    obligations the adapter must consume (e.g. {@link PublicProjection})
     * @param policyVersion  the engine's policy version for audit traceability
     */
    record Allowed<Q>(Q constraint, List<DecisionObligation> obligations, String policyVersion)
            implements AuthorizedQueryPlan<Q> {

        public Allowed {
            Objects.requireNonNull(constraint, "constraint");
            obligations = List.copyOf(Objects.requireNonNull(obligations, "obligations"));
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }

    /**
     * The query is denied. The product adapter must not execute the query at all.
     *
     * @param reasonCode    stable reason code (e.g. {@link AuthorizationReasonCodes#NO_BINDING})
     * @param policyVersion the engine's policy version
     */
    record Denied<Q>(String reasonCode, String policyVersion) implements AuthorizedQueryPlan<Q> {

        public Denied {
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }
}
