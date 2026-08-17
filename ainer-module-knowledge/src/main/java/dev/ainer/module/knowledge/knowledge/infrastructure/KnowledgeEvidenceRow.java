package dev.ainer.module.knowledge.knowledge.infrastructure;
import java.time.Instant;
import java.util.UUID;

/** Row for knowledge module. */
public class KnowledgeEvidenceRow {

    private UUID id;
    private UUID revisionId;
    private String linkType;
    private String targetRef;

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public UUID getRevisionId() { return revisionId; }

    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }

    public String getLinkType() { return linkType; }

    public void setLinkType(String linkType) { this.linkType = linkType; }

    public String getTargetRef() { return targetRef; }

    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }

}
