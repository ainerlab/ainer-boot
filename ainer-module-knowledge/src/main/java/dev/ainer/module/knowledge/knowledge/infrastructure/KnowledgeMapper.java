package dev.ainer.module.knowledge.knowledge.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface KnowledgeMapper {

    void insertObject(KnowledgeObjectRow row);

    KnowledgeObjectRow selectObject(@Param("id") UUID id);

    List<KnowledgeObjectRow> pageObjects(@Param("workspaceId") UUID workspaceId,
            @Param("offset") long offset, @Param("limit") int limit);

    long countObjects(@Param("workspaceId") UUID workspaceId);

    void insertRevision(KnowledgeRevisionRow row);

    void insertSources(@Param("revisionId") UUID revisionId,
            @Param("rows") List<KnowledgeSourceRow> rows);

    void insertEvidence(@Param("revisionId") UUID revisionId,
            @Param("rows") List<KnowledgeEvidenceRow> rows);

    KnowledgeRevisionRow selectRevision(@Param("id") UUID id);

    List<KnowledgeSourceRow> selectSources(@Param("revisionId") UUID revisionId);

    List<KnowledgeEvidenceRow> selectEvidence(@Param("revisionId") UUID revisionId);

    Long selectMaxRevisionNumber(@Param("objectId") UUID objectId);

    List<KnowledgeRevisionRow> selectRevisionsByObject(@Param("objectId") UUID objectId);

    KnowledgeRevisionRow selectPublishedAt(@Param("objectId") UUID objectId,
            @Param("asOf") Instant asOf);

    void insertLineage(@Param("from") UUID from, @Param("to") UUID to, @Param("at") Instant at);

    int markPublished(@Param("id") UUID id, @Param("at") Instant at);

    void insertLifecycle(KnowledgeLifecycleRow row);

    long countLifecycle(@Param("objectId") UUID objectId);
}
