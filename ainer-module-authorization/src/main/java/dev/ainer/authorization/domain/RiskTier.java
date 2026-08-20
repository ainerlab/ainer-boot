package dev.ainer.authorization.domain;

/**
 * 权限上附带的风险层级（ADR-0030 §3.1、§6）。更高层级可把 ALLOW 改道为
 * {@link AuthorizationOutcome#CHALLENGE}，要求 step-up、交易确认或人工审批。
 */
public enum RiskTier {
    LOW,
    MEDIUM,
    HIGH
}
