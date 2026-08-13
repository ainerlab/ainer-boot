package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class AiContextSnapshotRow {
    private UUID id;
    private UUID identityId;
    private UUID identityVersionId;
    private String evidenceRefs;
    private String memoryRefs;
    private Instant asOf;
    private Integer schemaVersion;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getIdentityId() { return identityId; }
    public void setIdentityId(UUID v) { this.identityId = v; }
    public UUID getIdentityVersionId() { return identityVersionId; }
    public void setIdentityVersionId(UUID v) { this.identityVersionId = v; }
    public String getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(String v) { this.evidenceRefs = v; }
    public String getMemoryRefs() { return memoryRefs; }
    public void setMemoryRefs(String v) { this.memoryRefs = v; }
    public Instant getAsOf() { return asOf; }
    public void setAsOf(Instant v) { this.asOf = v; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer v) { this.schemaVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
