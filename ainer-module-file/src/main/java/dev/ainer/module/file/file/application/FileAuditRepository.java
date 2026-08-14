package dev.ainer.module.file.file.application;

import dev.ainer.module.file.file.domain.FileAudit;

/**
 * Persistence port for append-only {@link FileAudit} rows (ADR-0040). Inserts join the caller's
 * transaction; audit failure rolls the mutation back.
 */
public interface FileAuditRepository {

    void insert(FileAudit audit);
}
