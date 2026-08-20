package dev.ainer.module.dictionary.dictionary.infrastructure;

import dev.ainer.module.dictionary.dictionary.application.DictionaryAuditRepository;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryAudit;
import org.springframework.stereotype.Repository;

/** {@code ainer_dictionary_audit} 的 MyBatis 适配器。 */
@Repository
public class MybatisDictionaryAuditRepository implements DictionaryAuditRepository {

    private final DictionaryAuditMapper mapper;

    public MybatisDictionaryAuditRepository(DictionaryAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(DictionaryAudit audit) {
        DictionaryAuditRow row = new DictionaryAuditRow();
        row.setId(audit.id());
        row.setOperation(audit.operation());
        row.setTargetKind(audit.targetKind());
        row.setTargetId(audit.targetId());
        row.setActorIssuer(audit.actorIssuer());
        row.setActorType(audit.actorType());
        row.setActorId(audit.actorId());
        row.setRequestId(audit.requestId());
        row.setDetail(audit.detail());
        row.setOccurredAt(audit.occurredAt());
        mapper.insert(row);
    }
}
