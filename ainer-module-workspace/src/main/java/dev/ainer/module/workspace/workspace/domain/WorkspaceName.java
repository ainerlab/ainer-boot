package dev.ainer.module.workspace.workspace.domain;

import java.util.Objects;

public record WorkspaceName(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 80;

    public WorkspaceName {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Workspace name length is invalid");
        }
    }
}
