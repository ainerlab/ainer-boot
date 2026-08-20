package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import org.springframework.security.authorization.AuthorizationResult;

import java.util.Objects;
import java.util.UUID;

/**
 * 以 Ainer {@link AuthorizationDecision} 为底座的 Spring Security
 * {@link AuthorizationResult}（ADR-0037 §4）。保留 decisionId、reasonCode 与结果，
 * 使 challenge/deny 场景能在审计中关联，而不必把更丰富的 Ainer 决策压扁成布尔值。
 *
 * <p>{@link #isGranted()} 只对没有未完成义务的 ALLOW 返回 {@code true}
 * （ADR-0030 §8.6：只有义务为空或已全部执行的 ALLOW 才能单独使用
 * AuthorizationManager）。带非空义务的 ALLOW 会以 OBLIGATION_UNHANDLED 拒绝，
 * 直到未来切片实现 {@code DecisionObligationExecutor}。
 *
 * <p>该类位于 {@code spring/} 适配器边界（ADR-0037 §3），是本包中唯一引用 Spring
 * Security 的类型。不得被 {@code domain/}、{@code policy/}、{@code catalog/} 或
 * {@code application/} 包导入。
 */
public final class AinerAuthorizationResult implements AuthorizationResult {

    private final AuthorizationDecision decision;

    public AinerAuthorizationResult(AuthorizationDecision decision) {
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    /** 底层的 Ainer 决策。 */
    public AuthorizationDecision decision() {
        return decision;
    }

    @Override
    public boolean isGranted() {
        if (decision.outcome() != AuthorizationOutcome.ALLOW) {
            return false;
        }
        // 带未完成义务的 ALLOW 不能由适配器单独放行（§8.6）。
        // 未来的 DecisionObligationExecutor 会消费义务；在那之前一律拒绝。
        // 义务槽位中的 PublicProjection 是 PUBLIC_PROJECTION 请求的响应投影数据，
        // 不是待执行义务——不得阻断放行。
        if (decision.obligations() == null || decision.obligations().isEmpty()) {
            return true;
        }
        return decision.obligations().stream()
                .allMatch(obligation -> obligation
                        instanceof dev.ainer.authorization.domain.PublicProjection);
    }

    /** 用于审计关联的稳定决策 id。 */
    public UUID decisionId() {
        return decision.decisionId();
    }

    /** 低基数 reason code（可安全写日志，但不得泄露给匿名客户端）。 */
    public String reasonCode() {
        return decision.reasonCode().value();
    }

    /** 决策结果（ALLOW / DENY / CHALLENGE）。 */
    public AuthorizationOutcome outcome() {
        return decision.outcome();
    }
}
