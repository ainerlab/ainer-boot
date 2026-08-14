package dev.ainer.module.dictionary.dictionary.application;

import java.util.List;

/** Generic pagination slice for dictionary queries (items + total). */
public record DictionaryPageSlice<T>(List<T> items, long total) {

    public DictionaryPageSlice {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
    }
}
