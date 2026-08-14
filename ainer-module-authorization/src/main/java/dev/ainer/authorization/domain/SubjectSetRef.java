package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Typed reference to a subject set owned by a product directory (ADR-0042 O2；承接 ADR-0032 §5.2
 * 的 post-Greenfield 语义）。Sets are binding targets only: they never enter JWTs, never act as
 * requesters, and never nest. {@code workspaceId} anchors the set to the same authorization
 * boundary as {@link Scope.Workspace}; {@code directoryId} is the optional owner-directory fact.
 */
public record SubjectSetRef(
        String objectType,
        UUID objectId,
        String relation,
        UUID workspaceId,
        @Nullable UUID directoryId) {

    private static final Pattern SAFE_TYPE = Pattern.compile("[a-z][a-z0-9.-]{0,63}");
    private static final Pattern SAFE_RELATION = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    public SubjectSetRef {
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(workspaceId, "workspaceId");
        String normalizedType = objectType.strip().toLowerCase();
        String normalizedRelation = relation.strip().toLowerCase();
        if (!SAFE_TYPE.matcher(normalizedType).matches()
                || !SAFE_RELATION.matcher(normalizedRelation).matches()) {
            throw new IllegalArgumentException("Invalid subject set reference");
        }
        objectType = normalizedType;
        relation = normalizedRelation;
    }
}
