package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Typed, verified authorization context (ADR-0030 §5.5). Only explicit fields are provided; there is no
 * arbitrary {@code Map<String,Object>}, SpEL, Rego, SQL or administrator-uploaded rule.
 *
 * @param evaluatedAt    decision time (also used for binding validity checks)
 * @param assurance      current authentication assurance strength
 * @param platformAppId  verified platform app / channel context, if any
 * @param requestId      correlation id, if any
 * @param traceId        trace id, if any
 */
public record AuthorizationContext(
        Instant evaluatedAt,
        Assurance assurance,
        @Nullable String platformAppId,
        @Nullable String requestId,
        @Nullable String traceId) {

    public AuthorizationContext {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(assurance, "assurance");
    }

    /** Authentication assurance strength (ADR-0030 §6.3). */
    public enum Assurance {
        NONE,
        RECENT_STRONG
    }
}
