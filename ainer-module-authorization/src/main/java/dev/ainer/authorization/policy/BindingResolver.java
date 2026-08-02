package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;

import java.util.Set;

/**
 * Resolves a subject's live {@link SubjectBinding}s (ADR-0030 §4, §12.1). The S0 in-memory implementation is
 * a test fixture; S1 replaces it with a PostgreSQL-backed resolver. Revocation is reflected immediately:
 * a still-valid JWT cannot restore a revoked database grant, and there is no ALLOW cache in the first version.
 */
public interface BindingResolver {

    Set<SubjectBinding> liveBindings(SubjectRef subject);
}
