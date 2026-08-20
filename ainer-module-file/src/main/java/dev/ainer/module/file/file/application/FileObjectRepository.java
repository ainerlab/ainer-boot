package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileObject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link FileObject} 元数据的持久化端口（ADR-0040）。
 */
public interface FileObjectRepository {

    void insert(FileObject object);

    Optional<FileObject> findById(UUID id);

    /**
     * 分页遍历元数据，可按 namespace 过滤，最新在前。
     *
     * @param namespace 可选的 namespace 过滤条件；{@code null} 或空白表示全部 namespace
     * @param offset    从零开始的行偏移，由调用方按 page/size 计算
     * @param size      页大小（调用方已校验为 1..100）
     */
    FilePageSlice findPage(@Nullable String namespace, long offset, int size);

    /** 删除元数据行。行不存在时返回 false。 */
    boolean deleteById(UUID id);

    /** 一页元数据及用于分页的总行数。 */
    record FilePageSlice(List<FileObject> items, long total) {
    }
}
