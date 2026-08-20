package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.SubjectId;

import java.util.Optional;
import java.util.UUID;

/**
 * Workspace 聚合的持久化端口。
 *
 * <p>{@link #update} 携带期望版本实现乐观并发控制；{@link #findByIdForUpdate} 提供
 * {@code FOR UPDATE} 行锁读取，供 OWNER 转移与恢复这类必须串行化的流程使用。
 * {@code findPage} 按成员主体过滤，只返回该主体可见的工作空间分页。
 */
public interface WorkspaceRepository {

    void insert(Workspace workspace);

    boolean update(Workspace workspace, long expectedVersion);

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByIdForUpdate(UUID id);

    WorkspacePage findPage(SubjectId subjectId, int page, int size, long offset);
}
