package {{package.name}}.{{entity.package}}.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** 仅供持久化使用的可变 Row；API 与应用层不会暴露此类型。 */
public class {{entity.className}}Row {

    private UUID id;
    private UUID workspaceId;
{{entity.rowFields}}
    private long version;
    private String createdBySubjectId;
    private String updatedBySubjectId;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

{{entity.rowAccessors}}

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getCreatedBySubjectId() {
        return createdBySubjectId;
    }

    public void setCreatedBySubjectId(String createdBySubjectId) {
        this.createdBySubjectId = createdBySubjectId;
    }

    public String getUpdatedBySubjectId() {
        return updatedBySubjectId;
    }

    public void setUpdatedBySubjectId(String updatedBySubjectId) {
        this.updatedBySubjectId = updatedBySubjectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
