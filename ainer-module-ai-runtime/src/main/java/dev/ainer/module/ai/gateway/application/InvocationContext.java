package dev.ainer.module.ai.gateway.application;

import java.util.Objects;

public record InvocationContext(String tenantId, String subjectId, String requestId) {

    public InvocationContext {
        tenantId = requireIdentifier(tenantId, "tenantId");
        subjectId = requireIdentifier(subjectId, "subjectId");
        requestId = requireIdentifier(requestId, "requestId");
    }

    private static String requireIdentifier(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > 128 || !value.matches("[A-Za-z0-9._:@/-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
