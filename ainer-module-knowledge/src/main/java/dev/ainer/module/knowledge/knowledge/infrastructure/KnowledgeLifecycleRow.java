package dev.ainer.module.knowledge.knowledge.infrastructure;
import java.time.Instant;
import java.util.UUID;

/** Row for knowledge module. */
public class KnowledgeLifecycleRow {

    private UUID id;
    private UUID objectId;
    private UUID revisionId;
    private String event;
    private String actorIssuer;
    private String actorType;
    private String actorId;
    private Instant occurredAt;

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public UUID getObjectId() { return objectId; }

    public void setObjectId(UUID objectId) { this.objectId = objectId; }

    public UUID getRevisionId() { return revisionId; }

    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }

    public String getEvent() { return event; }

    public void setEvent(String event) { this.event = event; }

    public String getActorIssuer() { return actorIssuer; }

    public void setActorIssuer(String actorIssuer) { this.actorIssuer = actorIssuer; }

    public String getActorType() { return actorType; }

    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getActorId() { return actorId; }

    public void setActorId(String actorId) { this.actorId = actorId; }

    public Instant getOccurredAt() { return occurredAt; }

    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

}
