package dev.ainer.module.file.file.infrastructure;

import dev.ainer.module.file.file.application.FileAuditRepository;
import dev.ainer.module.file.file.domain.FileAudit;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for {@code ainer_file_audit}. */
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
