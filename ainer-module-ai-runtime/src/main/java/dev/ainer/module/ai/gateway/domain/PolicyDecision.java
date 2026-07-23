package dev.ainer.module.ai.gateway.domain;

public enum PolicyDecision {
    ALLOWED,
    REJECTED_MODEL,
    REJECTED_PROMPT_SIZE,
    REJECTED_SENSITIVE_DATA,
    REJECTED_RATE_LIMIT,
    REJECTED_BUDGET
}
