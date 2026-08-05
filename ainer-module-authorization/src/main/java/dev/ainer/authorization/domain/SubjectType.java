package dev.ainer.authorization.domain;

/**
 * Trusted authenticated subject type. Per ADR-0030 §2.4, the first version only models USER and SERVICE
 * as request principals; AGENT appears as an execution participant (ADR-0031), not an actor type, and
 * PUBLIC/Group are not request principals.
 */
public enum SubjectType {
    USER,
    SERVICE
}
