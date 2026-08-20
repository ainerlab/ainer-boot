package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileObject;

import java.util.List;

/** 文件元数据的分页信封（items、page、size、total）。 */
public record FilePage(List<FileObject> items, int page, int size, long total) {

    public FilePage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("page and size must be positive, total non-negative");
        }
    }
}
