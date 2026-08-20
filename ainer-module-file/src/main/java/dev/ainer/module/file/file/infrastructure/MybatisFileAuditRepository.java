package dev.ainer.module.file.file.infrastructure;

import dev.ainer.module.file.file.application.FileAuditRepository;
import dev.ainer.module.file.file.domain.FileAudit;
import org.springframework.stereotype.Repository;

/** {@code ainer_file_audit} 的 MyBatis 适配器。 */
@Repository
public class MybatisFileAuditRepository implements FileAuditRepository {

    private final FileAuditMapper mapper;

    public MybatisFileAuditRepository(FileAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(FileAudit audit) {
        FileAuditRow row = new FileAuditRow();
        row.setId(audit.id());
        row.setFileId(audit.fileId());
        row.setOperation(audit.operation());
        row.setNamespace(audit.namespace());
        row.setActorIssuer(audit.actorIssuer());
        row.setActorType(audit.actorType());
        row.setActorId(audit.actorId());
        row.setRequestId(audit.requestId());
        row.setOccurredAt(audit.occurredAt());
        mapper.insert(row);
    }
}
