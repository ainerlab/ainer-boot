package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileObject;

import java.util.List;

/** Pagination envelope for file metadata (items, page, size, total). */
public record FilePage(List<FileObject> items, int page, int size, long total) {

    public FilePage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("page and size must be positive, total non-negative");
        }
    }
}
