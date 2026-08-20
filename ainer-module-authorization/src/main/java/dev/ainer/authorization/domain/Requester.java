package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 授权请求的发起者（ADR-0030 §2.3）。{@link Authenticated} 携带已验证主体及其 OAuth
 * scope 上限；{@link Anonymous} 是未认证调用方，永远不会成为 {@code PUBLIC} actor。
 * 匿名调用方只能使用 {@link AccessMode#PUBLIC_PROJECTION}。
 */
public sealed interface Requester permits Requester.Authenticated, Requester.Anonymous {

    /** 由安全层（而非本模块）解析出的已认证主体事实。 */
    record Authenticated(
            SubjectRef subjectRef,
            Set<String> scopeCeiling,
            Set<String> audiences,
            @Nullable String clientId) implements Requester {

        public Authenticated {
            Objects.requireNonNull(subjectRef, "subjectRef");
            Objects.requireNonNull(scopeCeiling, "scopeCeiling");
            Objects.requireNonNull(audiences, "audiences");
            scopeCeiling = Set.copyOf(scopeCeiling);
            audiences = Set.copyOf(audiences);
        }
    }

    /** 未认证调用方。 */
    record Anonymous() implements Requester {
    }
}
