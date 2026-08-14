package dev.ainer.module.file.file.api;

import dev.ainer.module.file.file.application.FilePage;

import java.util.List;

/** Pagination envelope for file metadata. */
public record FilePageResponse(List<FileResponse> items, int page, int size, long total) {

    public static FilePageResponse from(FilePage page) {
        return new FilePageResponse(
                page.items().stream().map(FileResponse::from).toList(),
                page.page(),
                page.size(),
                page.total());
    }
}
