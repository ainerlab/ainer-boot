package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reference to a trusted subject (ADR-0030 §2.6). {@code issuerNamespace} prevents identical {@code sub}
 * claims from different issuers colliding. Type is {@link SubjectType#USER} or {@link SubjectType#SERVICE}
 * in the first version.
 */
public record SubjectRef(String issuerNamespace, String subjectId, SubjectType type) {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public SubjectRef {
        Objects.requireNonNull(issuerNamespace, "issuerNamespace");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(type, "type");
        String normalizedNamespace = issuerNamespace.trim();
        String normalizedSubject = subjectId.trim();
        if (!SAFE_IDENTIFIER.matcher(normalizedNamespace).matches()
                || !SAFE_IDENTIFIER.matcher(normalizedSubject).matches()) {
            throw new IllegalArgumentException("Invalid subject reference");
        }
        issuerNamespace = normalizedNamespace;
        subjectId = normalizedSubject;
    }
}
