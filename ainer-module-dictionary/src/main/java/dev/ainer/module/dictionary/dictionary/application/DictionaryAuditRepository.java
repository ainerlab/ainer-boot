package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryAudit;

/**
 * 只追加 {@link DictionaryAudit} 行的持久化端口（ADR-0040）。插入加入调用方事务；
 * 审计失败会连同业务变更一起回滚。
 */
public interface DictionaryAuditRepository {

    void insert(DictionaryAudit audit);
}
