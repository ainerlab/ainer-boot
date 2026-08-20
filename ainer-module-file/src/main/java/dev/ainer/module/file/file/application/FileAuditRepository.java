package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileAudit;

/**
 * 只追加 {@link FileAudit} 行的持久化端口（ADR-0040）。插入加入调用方事务；
 * 审计失败会连同业务变更一起回滚。
 */
public interface FileAuditRepository {

    void insert(FileAudit audit);
}
