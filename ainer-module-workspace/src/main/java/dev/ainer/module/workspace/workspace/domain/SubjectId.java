package dev.ainer.module.workspace.workspace.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record SubjectId(String value) {

    public static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public SubjectId {
        value = Objects.requireNonNull(value, "value").trim();
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Subject identifier is invalid");
        }
    }
}
