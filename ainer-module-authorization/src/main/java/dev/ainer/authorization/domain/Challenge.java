package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 类型化授权挑战（ADR-0030 §6.3）。CHALLENGE 结果表示在挑战被满足并携带新的认证结果
 * 重新求值之前，动作不得执行。S0 只实现 {@link AuthenticationChallenge}；交易确认与
 * 人工审批是预留的类型边界，不是活跃取值。
 */
public sealed interface Challenge permits Challenge.AuthenticationChallenge {

    /** 要求主体完成近期强认证（RFC 9470 Step-up）。 */
    record AuthenticationChallenge(AuthorizationContext.Assurance requiredAssurance) implements Challenge {

        public AuthenticationChallenge {
            Objects.requireNonNull(requiredAssurance, "requiredAssurance");
        }
    }
}
