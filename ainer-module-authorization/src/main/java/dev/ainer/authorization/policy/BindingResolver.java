package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetBinding;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Resolves a subject's live {@link SubjectBinding}s (ADR-0030 §4, §12.1). The S0 in-memory implementation is
 * a test fixture; S1 replaces it with a PostgreSQL-backed resolver. Revocation is reflected immediately:
 * a still-valid JWT cannot restore a revoked database grant, and there is no ALLOW cache in the first version.
 */
public interface BindingResolver {

    Set<SubjectBinding> liveBindings(SubjectRef subject);

    /**
     * Live set bindings whose scope covers the resource at {@code at} (ADR-0042 O2). The
     * decision engine additionally checks requester membership per candidate — subject match
     * happens through the set, not through this query. Default empty keeps S0 fixtures and
     * external consumers source-compatible.
     */
    default List<SubjectSetBinding> liveSetBindings(ResourceRef resource, Instant at) {
        return List.of();
    }
}
