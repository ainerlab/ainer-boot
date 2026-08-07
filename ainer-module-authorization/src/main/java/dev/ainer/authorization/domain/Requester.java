package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * The authorization requester (ADR-0030 §2.3). {@link Authenticated} carries a verified principal plus its
 * OAuth scope ceiling; {@link Anonymous} is an unauthenticated caller and is never a {@code PUBLIC} actor.
 * Anonymous callers may only use {@link AccessMode#PUBLIC_PROJECTION}.
 */
public sealed interface Requester permits Requester.Authenticated, Requester.Anonymous {

    /** Authenticated principal facts resolved by the security layer (not by this module). */
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

    /** Unauthenticated caller. */
    record Anonymous() implements Requester {
    }
}
