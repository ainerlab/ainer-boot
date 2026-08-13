package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Request to authorize a collection/list query (ADR-0030 §7, S3).
 *
 * <p>Unlike {@link AuthorizationRequest} which targets a single concrete resource, this request
 * targets a resource <em>type</em> and asks the authorization engine to produce a typed query
 * constraint ({@code Q}) that the product repository/search adapter must apply to exclude
 * unauthorized rows at the database level. Ainer never outputs SQL strings, table names, column
 * names or search DSL — it only produces the typed {@code Q} that the product adapter translates.
 *
 * @param requester       who is asking
 * @param accessMode      {@link AccessMode#PUBLIC_PROJECTION} or {@link AccessMode#AUTHENTICATED}
 * @param permission      the permission being requested (e.g. a {@code *.list.read})
 * @param resourceType    the resource type being queried
 * @param queryPurpose    a stable, human-readable purpose tag for audit/metrics (not a raw SQL fragment)
 * @param requestedQuery  product-defined, already-input-validated query intent (e.g. filters, sort keys)
 * @param context         evaluation context (time, assurance, trace)
 * @param <I>             product query-intent type
 */
public record QueryAuthorizationRequest<I>(
        Requester requester,
        AccessMode accessMode,
        PermissionCode permission,
        ResourceType resourceType,
        String queryPurpose,
        I requestedQuery,
        AuthorizationContext context) {

    public QueryAuthorizationRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(accessMode, "accessMode");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(queryPurpose, "queryPurpose");
        Objects.requireNonNull(context, "context");
        if (queryPurpose.isBlank()) {
            throw new IllegalArgumentException("queryPurpose must not be blank");
        }
        // requestedQuery may be null if the product has no additional intent.
    }
}
