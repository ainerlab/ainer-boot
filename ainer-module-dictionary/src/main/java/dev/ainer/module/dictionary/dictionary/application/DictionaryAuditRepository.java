package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryAudit;

/**
 * Persistence port for append-only {@link DictionaryAudit} rows (ADR-0040). Inserts join the
 * caller's transaction; audit failure rolls the mutation back.
 */
public interface DictionaryAuditRepository {

    void insert(DictionaryAudit audit);
}
