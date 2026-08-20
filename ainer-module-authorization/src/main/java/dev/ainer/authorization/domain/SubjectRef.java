package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可信主体引用（ADR-0030 §2.6）。{@code issuerNamespace} 防止不同 issuer 的相同
 * {@code sub} 声明发生碰撞。第一版中类型只允许 {@link SubjectType#USER} 或
 * {@link SubjectType#SERVICE}。
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
