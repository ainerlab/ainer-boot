package dev.ainer.authorization.domain;

/**
 * 授权求值的结果（ADR-0030 §6）。{@link #CHALLENGE} 表示在所要求的因子/确认/审批被满足
 * 之前动作不得继续，满足后必须重新求值；它不是 ALLOW。
 */
public enum AuthorizationOutcome {
    ALLOW,
    DENY,
    CHALLENGE
}
