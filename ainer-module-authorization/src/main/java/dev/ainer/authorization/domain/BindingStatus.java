package dev.ainer.authorization.domain;

/**
 * {@link SubjectBinding} 的生命周期状态（ADR-0030 §4.1、§11.2）。撤销是逻辑状态迁移，
 * 不是物理删除。
 */
public enum BindingStatus {
    ACTIVE,
    REVOKED
}
