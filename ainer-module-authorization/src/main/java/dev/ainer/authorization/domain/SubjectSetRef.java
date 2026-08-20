package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 指向产品目录所拥有主体集合的类型化引用（ADR-0042 O2；承接 ADR-0032 §5.2 的
 * post-Greenfield 语义）。集合只是绑定目标：绝不进入 JWT、绝不充当请求者、绝不嵌套。
 * {@code workspaceId} 把集合锚定到与 {@link Scope.Workspace} 相同的授权边界；
 * {@code directoryId} 是可选的归属目录事实。
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
