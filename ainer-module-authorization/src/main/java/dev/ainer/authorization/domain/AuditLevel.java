package dev.ainer.authorization.domain;

/**
 * 权限上附带的审计级别（ADR-0030 §3.1、§12）。控制一次决策是否持久化到授权决策审计。
 */
public enum AuditLevel {
    /** 不写决策行（典型场景是批量公开读取）。 */
    NONE,
    /** 仅对受保护动作的 ALLOW / DENY / CHALLENGE 写决策行。 */
    ON_DECISION,
    /** 每次求值都审计。 */
    ALWAYS
}
