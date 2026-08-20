package dev.ainer.authorization.domain;

/**
 * 可信的已认证主体类型。按 ADR-0030 §2.4，第一版只把 USER 和 SERVICE 建模为请求主体；
 * AGENT 作为执行参与者出现（ADR-0031），不是 actor 类型；PUBLIC/Group 不是请求主体。
 */
public enum SubjectType {
    USER,
    SERVICE
}
