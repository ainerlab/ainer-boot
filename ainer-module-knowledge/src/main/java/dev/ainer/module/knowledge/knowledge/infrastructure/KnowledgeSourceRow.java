package dev.ainer.module.knowledge.knowledge.infrastructure;
import java.time.Instant;
import java.util.UUID;

/** Row for knowledge module. */
public class KnowledgeSourceRow {

    private UUID id;
    private UUID revisionId;
    private String sourceType;
    private String sourceRef;

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public UUID getRevisionId() { return revisionId; }

    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }

    public String getSourceType() { return sourceType; }

    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceRef() { return sourceRef; }

    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

}
