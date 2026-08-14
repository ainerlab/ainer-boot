package dev.ainer.module.organization.orgdir.domain;

/** 人工门禁状态；未来开始/自然结束由有效期决定（ADR-0042 §3）。 */
public enum OrgStatus {
    ENABLED,
    SUSPENDED,
    REVOKED
}
