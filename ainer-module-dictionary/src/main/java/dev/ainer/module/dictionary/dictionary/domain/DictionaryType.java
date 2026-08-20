package dev.ainer.module.dictionary.dictionary.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 树形结构的字典分类（ADR-0038）。通过 parentId 支持无限层级嵌套。
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
