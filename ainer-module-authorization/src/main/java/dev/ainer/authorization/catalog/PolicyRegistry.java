package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;

import java.util.Objects;

/**
 * Validates at startup that every registered {@link dev.ainer.authorization.domain Permission} has a
 * declared {@link GrantPath} from the {@link DomainAuthorizationPolicy} (ADR-0030 §5.1, §3.2). If any
 * Permission is uncovered (no declared path), the application fails to start — this is the "fail closed
 * on unknown or conflicting policy" requirement.
 */
public final class PolicyRegistry {

    /**
     * @throws IllegalStateException if any registered Permission has no declared GrantPath
     */
    public void validate(PermissionRegistry permissions, DomainAuthorizationPolicy policy) {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(policy, "policy");
        for (var permission : permissions.snapshot()) {
            GrantPath path = policy.pathFor(permission.code());
            if (path == null) {
                throw new IllegalStateException(
                        "No grant path declared for permission '%s'".formatted(permission.code().value()));
            }
        }
    }
}
