package dev.ainer.module.dictionary.dictionary.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link DictionaryType} 内的单个字典条目（ADR-0038）。
 */
public record DictionaryItem(
        UUID id,
        UUID typeId,
        String code,
        String label,
        String labelEn,
        String value,
        int sortIndex,
        DictionaryStatus status,
        String cssClass,
        String remark,
        long version) {

    public DictionaryItem {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(status, "status");
        code = code.trim();
        label = label.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (label.isEmpty()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
