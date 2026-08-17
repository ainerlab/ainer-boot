package dev.ainer.module.knowledge.knowledge.application;

import dev.ainer.module.knowledge.knowledge.domain.KnowledgeEvidence;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeObject;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeRevision;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Knowledge 持久化端口（ADR-0044）。 */
public interface KnowledgeRepository {

    void insertObject(KnowledgeObject object);

    Optional<KnowledgeObject> findObject(UUID id);

    List<KnowledgeObject> pageObjects(UUID workspaceId, long offset, int limit);

    long countObjects(UUID workspaceId);

    /** 提案：revision（PROPOSED）+ sources + evidence 一次插入，返回生成的 revision ID。 */
    UUID insertRevision(KnowledgeRevision revision, List<KnowledgeSource> sources,
            List<KnowledgeEvidence> evidence);

    Optional<KnowledgeRevision> findRevision(UUID revisionId);

    List<KnowledgeRevision> findRevisionsByObject(UUID objectId);

    /** asOf 时刻已发布的最新版本（published_at <= asOf），未发布不可见。 */
    Optional<KnowledgeRevision> findPublishedRevisionAt(UUID objectId, Instant asOf);

    long nextRevisionNumber(UUID objectId);

    void insertLineage(UUID fromRevisionId, UUID toRevisionId, Instant at);

    /** 发布投影：PROPOSED → PUBLISHED，写 published_at；负载列永不更新。 */
    boolean markPublished(UUID revisionId, Instant publishedAt);

    void insertLifecycleEvent(dev.ainer.module.knowledge.knowledge.domain.KnowledgeLifecycleEvent event);

    long countLifecycleEvents(UUID objectId);
}
