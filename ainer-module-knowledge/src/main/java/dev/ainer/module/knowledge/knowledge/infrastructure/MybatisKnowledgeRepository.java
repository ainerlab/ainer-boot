package dev.ainer.module.knowledge.knowledge.infrastructure;

import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.knowledge.knowledge.application.KnowledgeRepository;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeEvidence;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeLifecycleEvent;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeObject;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeRevision;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeSource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MyBatis adapter for the knowledge module (ADR-0044). */
@Repository
public class MybatisKnowledgeRepository implements KnowledgeRepository {

    private final KnowledgeMapper mapper;

    public MybatisKnowledgeRepository(KnowledgeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertObject(KnowledgeObject object) {
        KnowledgeObjectRow row = new KnowledgeObjectRow();
        row.setId(object.id());
        row.setWorkspaceId(object.workspaceId());
        row.setKind(object.kind());
        row.setTitle(object.title());
        row.setStatus(object.status());
        row.setCreatedAt(object.createdAt());
        row.setUpdatedAt(object.updatedAt());
        mapper.insertObject(row);
    }

    @Override
    public Optional<KnowledgeObject> findObject(UUID id) {
        return Optional.ofNullable(mapper.selectObject(id)).map(MybatisKnowledgeRepository::toObject);
    }

    @Override
    public List<KnowledgeObject> pageObjects(UUID workspaceId, long offset, int limit) {
        return mapper.pageObjects(workspaceId, offset, limit).stream()
                .map(MybatisKnowledgeRepository::toObject).toList();
    }

    @Override
    public long countObjects(UUID workspaceId) {
        return mapper.countObjects(workspaceId);
    }

    @Override
    public UUID insertRevision(
            KnowledgeRevision revision, List<KnowledgeSource> sources, List<KnowledgeEvidence> evidence) {
        KnowledgeRevisionRow row = new KnowledgeRevisionRow();
        row.setId(revision.id());
        row.setObjectId(revision.objectId());
        row.setRevisionNumber(revision.revisionNumber());
        row.setPayloadMarkdown(revision.payloadMarkdown());
        row.setStatus(revision.status());
        row.setCreatedByIssuer(revision.createdByIssuer());
        row.setCreatedByType(revision.createdByType());
        row.setCreatedById(revision.createdById());
        row.setCreatedAt(revision.createdAt());
        row.setPublishedAt(revision.publishedAt());
        mapper.insertRevision(row);
        if (!sources.isEmpty()) {
            List<KnowledgeSourceRow> sourceRows = sources.stream().map(source -> {
                KnowledgeSourceRow sourceRow = new KnowledgeSourceRow();
                sourceRow.setId(Uuidv7.generate());
                sourceRow.setRevisionId(revision.id());
                sourceRow.setSourceType(source.sourceType());
                sourceRow.setSourceRef(source.sourceRef());
                return sourceRow;
            }).toList();
            mapper.insertSources(revision.id(), sourceRows);
        }
        if (!evidence.isEmpty()) {
            List<KnowledgeEvidenceRow> evidenceRows = evidence.stream().map(link -> {
                KnowledgeEvidenceRow evidenceRow = new KnowledgeEvidenceRow();
                evidenceRow.setId(Uuidv7.generate());
                evidenceRow.setRevisionId(revision.id());
                evidenceRow.setLinkType(link.linkType());
                evidenceRow.setTargetRef(link.targetRef());
                return evidenceRow;
            }).toList();
            mapper.insertEvidence(revision.id(), evidenceRows);
        }
        return revision.id();
    }

    @Override
    public Optional<KnowledgeRevision> findRevision(UUID revisionId) {
        return Optional.ofNullable(mapper.selectRevision(revisionId))
                .map(row -> toRevision(row, true));
    }

    @Override
    public List<KnowledgeRevision> findRevisionsByObject(UUID objectId) {
        return mapper.selectRevisionsByObject(objectId).stream()
                .map(row -> toRevision(row, false)).toList();
    }

    @Override
    public Optional<KnowledgeRevision> findPublishedRevisionAt(UUID objectId, Instant asOf) {
        return Optional.ofNullable(mapper.selectPublishedAt(objectId, asOf))
                .map(row -> toRevision(row, true));
    }

    @Override
    public long nextRevisionNumber(UUID objectId) {
        Long max = mapper.selectMaxRevisionNumber(objectId);
        return max == null ? 1L : max + 1L;
    }

    @Override
    public void insertLineage(UUID fromRevisionId, UUID toRevisionId, Instant at) {
        mapper.insertLineage(fromRevisionId, toRevisionId, at);
    }

    @Override
    public boolean markPublished(UUID revisionId, Instant publishedAt) {
        return mapper.markPublished(revisionId, publishedAt) == 1;
    }

    @Override
    public void insertLifecycleEvent(KnowledgeLifecycleEvent event) {
        KnowledgeLifecycleRow row = new KnowledgeLifecycleRow();
        row.setId(event.id());
        row.setObjectId(event.objectId());
        row.setRevisionId(event.revisionId());
        row.setEvent(event.event());
        row.setActorIssuer(event.actorIssuer());
        row.setActorType(event.actorType());
        row.setActorId(event.actorId());
        row.setOccurredAt(event.occurredAt());
        mapper.insertLifecycle(row);
    }

    @Override
    public long countLifecycleEvents(UUID objectId) {
        return mapper.countLifecycle(objectId);
    }

    private static KnowledgeObject toObject(KnowledgeObjectRow row) {
        return new KnowledgeObject(row.getId(), row.getWorkspaceId(), row.getKind(),
                row.getTitle(), row.getStatus(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private KnowledgeRevision toRevision(KnowledgeRevisionRow row, boolean withChildren) {
        List<KnowledgeSource> sources = withChildren
                ? mapper.selectSources(row.getId()).stream().map(sourceRow -> new KnowledgeSource(
                        sourceRow.getId(), sourceRow.getRevisionId(), sourceRow.getSourceType(),
                        sourceRow.getSourceRef())).toList()
                : List.of();
        List<KnowledgeEvidence> evidence = withChildren
                ? mapper.selectEvidence(row.getId()).stream().map(evidenceRow -> new KnowledgeEvidence(
                        evidenceRow.getId(), evidenceRow.getRevisionId(), evidenceRow.getLinkType(),
                        evidenceRow.getTargetRef())).toList()
                : List.of();
        return new KnowledgeRevision(row.getId(), row.getObjectId(), row.getRevisionNumber(),
                row.getPayloadMarkdown(), row.getStatus(), row.getCreatedByIssuer(),
                row.getCreatedByType(), row.getCreatedById(), row.getCreatedAt(),
                row.getPublishedAt(), sources, evidence);
    }
}
