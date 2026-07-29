package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class AiResultRow {
    private UUID id;
    private UUID runId;
    private UUID invocationId;
    private String content;
    private String factRefs;
    private String inferences;
    private Integer resultSchemaVersion;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID v) { this.runId = v; }
    public UUID getInvocationId() { return invocationId; }
    public void setInvocationId(UUID v) { this.invocationId = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public String getFactRefs() { return factRefs; }
    public void setFactRefs(String v) { this.factRefs = v; }
    public String getInferences() { return inferences; }
    public void setInferences(String v) { this.inferences = v; }
    public Integer getResultSchemaVersion() { return resultSchemaVersion; }
    public void setResultSchemaVersion(Integer v) { this.resultSchemaVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
