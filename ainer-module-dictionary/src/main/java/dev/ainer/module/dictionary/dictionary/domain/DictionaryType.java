package dev.ainer.module.dictionary.dictionary.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Tree-structured dictionary classification (ADR-0038). Supports unlimited nesting via parentId.
 */
public record DictionaryType(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String nameEn,
        String description,
        DictionaryStatus status,
        int sortIndex,
        long version) {

    public DictionaryType {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        code = code.trim();
        name = name.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
