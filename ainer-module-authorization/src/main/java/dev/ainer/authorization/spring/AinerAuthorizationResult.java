package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import org.springframework.security.authorization.AuthorizationResult;

import java.util.Objects;
import java.util.UUID;

/**
 * Spring Security {@link AuthorizationResult} backed by an Ainer {@link AuthorizationDecision}
 * (ADR-0037 §4). Preserves the decisionId, reasonCode and outcome so that challenge/deny scenarios
 * can be correlated in audit without flattening the richer Ainer decision into a boolean.
 *
 * <p>{@link #isGranted()} returns {@code true} only for ALLOW with no outstanding obligations
 * (ADR-0030 §8.6: only ALLOW with empty obligations or fully-executed obligations may use the
 * AuthorizationManager alone). ALLOW with non-empty obligations is denied as OBLIGATION_UNHANDLED
 * until a {@code DecisionObligationExecutor} is implemented (future slice).
 *
 * <p>This class lives in the {@code spring/} adapter boundary (ADR-0037 §3) and is the only type
 * in this package that references Spring Security. It must not be imported by {@code domain/},
 * {@code policy/}, {@code catalog/} or {@code application/} packages.
 */
public final class AinerAuthorizationResult implements AuthorizationResult {

    private final AuthorizationDecision decision;

    public AinerAuthorizationResult(AuthorizationDecision decision) {
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    /** The underlying Ainer decision. */
    public AuthorizationDecision decision() {
        return decision;
    }

    @Override
    public boolean isGranted() {
        if (decision.outcome() != AuthorizationOutcome.ALLOW) {
            return false;
        }
        // ALLOW with outstanding obligations is not grantable by the adapter alone (§8.6).
        // A future DecisionObligationExecutor will consume obligations; until then, deny.
        // A PublicProjection carried in the obligations slot is projected response data for
        // PUBLIC_PROJECTION requests, not a pending obligation — it must not block the grant.
        if (decision.obligations() == null || decision.obligations().isEmpty()) {
            return true;
        }
        return decision.obligations().stream()
                .allMatch(obligation -> obligation
                        instanceof dev.ainer.authorization.domain.PublicProjection);
    }

    /** Stable decision id for audit correlation. */
    public UUID decisionId() {
        return decision.decisionId();
    }

    /** Low-cardinality reason code (safe to log, not to leak to anonymous clients). */
    public String reasonCode() {
        return decision.reasonCode().value();
    }

    /** The outcome (ALLOW / DENY / CHALLENGE). */
    public AuthorizationOutcome outcome() {
        return decision.outcome();
    }
}
