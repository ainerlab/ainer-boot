package {{package.name}}.{{entity.package}}.application;

import {{package.name}}.{{entity.package}}.infrastructure.{{entity.className}}Mapper;
import dev.ainer.core.uuid.Uuidv7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 在业务事务之外独立持久化资源授权决策。插入失败会向上传播，使受保护用例失败关闭而不是丢失审计。
 */
@Service
public class {{entity.className}}AccessAuditService {

    private final {{entity.className}}Mapper mapper;
    private final Clock clock;

    public {{entity.className}}AccessAuditService({{entity.className}}Mapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID workspaceId,
            UUID resourceId,
            String actorSubjectId,
            String action,
            String decision,
            String reasonCode,
            String requestId) {
        int inserted = mapper.insertAccessAudit(
                Uuidv7.generate(), workspaceId, resourceId, actorSubjectId,
                action, decision, reasonCode, requestId, clock.instant());
        if (inserted != 1) {
            throw new IllegalStateException("authorization audit insert did not affect exactly one row");
        }
    }
}
