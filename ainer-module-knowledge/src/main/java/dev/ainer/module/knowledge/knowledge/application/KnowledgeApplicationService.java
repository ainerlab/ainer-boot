package dev.ainer.module.knowledge.knowledge.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeEvidence;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeLifecycleEvent;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeObject;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeRevision;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeSource;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Knowledge 应用服务（ADR-0044 K1/K2）。Revision 负载不可变（更新 = supersede 新版本 +
 * SUPERSEDES lineage）；PUBLISH 是人工门禁（SERVICE 提案允许、发布 403）；读取解析只暴露已
 * 发布版本并 pin 精确 Revision；生命周期事件同事务 append-only。时间入口统一微秒截断
 * （timestamptz 精度，见组织模块同类修复）。
 */
@Service
public class KnowledgeApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern NAMESPACED_KIND = Pattern.compile("[a-z][a-z0-9-]{0,31}\\.[a-z][a-z0-9._-]{0,95}");

    private final KnowledgeRepository repository;
    private final Clock clock;

    public KnowledgeApplicationService(KnowledgeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeObject createObject(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            UUID workspaceId, String kind, String title) {
        requireManage(principal);
        Objects.requireNonNull(workspaceId, "workspaceId");
        String normalizedKind = kind == null ? "" : kind.strip();
        if (!NAMESPACED_KIND.matcher(normalizedKind).matches()) {
            throw new BusinessException(KnowledgeErrorCode.INVALID_KIND);
        }
        if (title == null || title.isBlank() || title.strip().length() > 256) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST, "title 不合法");
        }
        Instant now = micros(clock.instant());
        KnowledgeObject object = new KnowledgeObject(Uuidv7.generate(), workspaceId,
                normalizedKind, title.strip(), "ACTIVE", now, now);
        repository.insertObject(object);
        lifecycle(object.id(), null, "OBJECT_CREATED", principal, now);
        return object;
    }

    /**
     * 提案新版本：USER 与 SERVICE 均可（AI 输出到此为止）。可选 basedOnRevisionId 形成显式
     * SUPERSEDES lineage；新版本号 = 对象当前最大版本 + 1。
     */
    @Transactional
    public KnowledgeRevision proposeRevision(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID objectId,
            String payloadMarkdown, List<KnowledgeSource> sources, List<KnowledgeEvidence> evidence,
            @Nullable UUID basedOnRevisionId) {
        requireManage(principal);
        KnowledgeObject object = requireObject(objectId);
        if (payloadMarkdown == null || payloadMarkdown.isBlank()) {
            throw new BusinessException(KnowledgeErrorCode.EMPTY_PAYLOAD);
        }
        List<KnowledgeSource> safeSources = sources == null ? List.of() : List.copyOf(sources);
        List<KnowledgeEvidence> safeEvidence = evidence == null ? List.of() : List.copyOf(evidence);
        for (KnowledgeSource source : safeSources) {
            String type = source.sourceType() == null ? "" : source.sourceType().strip();
            if (!NAMESPACED_KIND.matcher(type).matches()) {
                throw new BusinessException(KnowledgeErrorCode.INVALID_KIND,
                        "sourceType 必须是 namespaced 受控字符串");
            }
            if (source.sourceRef() == null || source.sourceRef().isBlank()
                    || source.sourceRef().length() > 512) {
                throw new BusinessException(KnowledgeErrorCode.EMPTY_PAYLOAD,
                        "sourceRef 不能为空且不超过 512 字符");
            }
        }
        for (KnowledgeEvidence link : safeEvidence) {
            String type = link.linkType() == null ? "" : link.linkType().strip();
            if (!"SUPPORTS".equals(type) && !"CONTRADICTS".equals(type)) {
                throw new BusinessException(KnowledgeErrorCode.EMPTY_PAYLOAD,
                        "linkType 只能是 SUPPORTS 或 CONTRADICTS");
            }
            if (link.targetRef() == null || link.targetRef().isBlank()
                    || link.targetRef().length() > 512) {
                throw new BusinessException(KnowledgeErrorCode.EMPTY_PAYLOAD,
                        "targetRef 不能为空且不超过 512 字符");
            }
        }
        KnowledgeRevision base = null;
        if (basedOnRevisionId != null) {
            base = repository.findRevision(basedOnRevisionId)
                    .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.INVALID_LINEAGE));
            if (!base.objectId().equals(objectId) || "RETIRED".equals(base.status())) {
                throw new BusinessException(KnowledgeErrorCode.INVALID_LINEAGE);
            }
        }
        Instant now = micros(clock.instant());
        UUID revisionId = Uuidv7.generate();
        // 并发提案会在 (object_id, revision_number) 上竞争；唯一约束是仲裁者——冲突时
        // 重读一次当前最大版本号，而不是把竞态暴露为 500。
        KnowledgeRevision revision = insertWithRevisionNumberRetry(objectId, payloadMarkdown,
                principal, now, safeSources, safeEvidence, revisionId);
        if (base != null) {
            repository.insertLineage(base.id(), revisionId, now);
        }
        lifecycle(objectId, revisionId, "PROPOSED", principal, now);
        return revision;
    }

    private KnowledgeRevision insertWithRevisionNumberRetry(
            UUID objectId, String payloadMarkdown, AuthenticatedPrincipal principal, Instant now,
            List<dev.ainer.module.knowledge.knowledge.domain.KnowledgeSource> safeSources,
            List<dev.ainer.module.knowledge.knowledge.domain.KnowledgeEvidence> safeEvidence,
            UUID revisionId) {
        org.springframework.dao.DuplicateKeyException conflict = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                KnowledgeRevision revision = new KnowledgeRevision(
                        revisionId, objectId, repository.nextRevisionNumber(objectId),
                        payloadMarkdown, "PROPOSED",
                        principal.authority().issuer(), principal.isService() ? "SERVICE" : "USER",
                        principal.subjectId(), now, null, safeSources, safeEvidence);
                repository.insertRevision(revision, safeSources, safeEvidence);
                return revision;
            } catch (org.springframework.dao.DuplicateKeyException race) {
                conflict = race;
            }
        }
        throw new BusinessException(KnowledgeErrorCode.EMPTY_PAYLOAD,
                "并发提案冲突，请重试: " + conflict.getMessage());
    }

    /**
     * 人工发布门禁（不变式 #9）：只有 USER principal 可以发布；SERVICE/AI 调用一律 403。
     */
    @Transactional
    public KnowledgeRevision publishRevision(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID revisionId) {
        requireManage(principal);
        if (principal.isService()) {
            throw new BusinessException(KnowledgeErrorCode.PUBLISH_REQUIRES_HUMAN);
        }
        KnowledgeRevision revision = repository.findRevision(revisionId)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.REVISION_NOT_FOUND));
        if (!"PROPOSED".equals(revision.status())) {
            throw new BusinessException("PUBLISHED".equals(revision.status())
                    ? KnowledgeErrorCode.ALREADY_PUBLISHED
                    : KnowledgeErrorCode.NOT_PROPOSED);
        }
        requireObject(revision.objectId());
        Instant now = micros(clock.instant());
        if (!repository.markPublished(revisionId, now)) {
            throw new BusinessException(KnowledgeErrorCode.NOT_PROPOSED);
        }
        lifecycle(revision.objectId(), revisionId, "PUBLISHED", principal, now);
        return repository.findRevision(revisionId).orElseThrow();
    }

    /** asOf 解析（K1）：返回当时已发布的精确 Revision pin；未发布版本对读取不可见。 */
    @Transactional(readOnly = true)
    public KnowledgeRevision resolveObject(
            AuthenticatedPrincipal principal, UUID objectId, @Nullable Instant asOf) {
        requireRead(principal);
        requireObject(objectId);
        Instant evaluationTime = asOf == null ? micros(clock.instant()) : micros(asOf);
        return repository.findPublishedRevisionAt(objectId, evaluationTime)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.REVISION_NOT_FOUND,
                        "该对象在评估时间没有已发布版本"));
    }

    @Transactional(readOnly = true)
    public KnowledgeRevision getRevision(AuthenticatedPrincipal principal, UUID revisionId) {
        requireRead(principal);
        return repository.findRevision(revisionId)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.REVISION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeObject> pageObjects(
            AuthenticatedPrincipal principal, UUID workspaceId, long page, long size) {
        requireRead(principal);
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(KnowledgeErrorCode.INVALID_PAGE);
        }
        return repository.pageObjects(workspaceId, (page - 1) * (int) size, (int) size);
    }

    @Transactional(readOnly = true)
    public long countObjects(AuthenticatedPrincipal principal, UUID workspaceId) {
        requireRead(principal);
        return repository.countObjects(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeRevision> revisionsOfObject(AuthenticatedPrincipal principal, UUID objectId) {
        requireRead(principal);
        requireObject(objectId);
        return repository.findRevisionsByObject(objectId);
    }

    @Transactional(readOnly = true)
    public long lifecycleEventCount(UUID objectId) {
        return repository.countLifecycleEvents(objectId);
    }

    private KnowledgeObject requireObject(UUID objectId) {
        KnowledgeObject object = repository.findObject(objectId)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.OBJECT_NOT_FOUND));
        if (!"ACTIVE".equals(object.status())) {
            throw new BusinessException(KnowledgeErrorCode.OBJECT_NOT_FOUND);
        }
        return object;
    }

    private void lifecycle(
            UUID objectId, @Nullable UUID revisionId, String event,
            AuthenticatedPrincipal principal, Instant at) {
        repository.insertLifecycleEvent(new KnowledgeLifecycleEvent(
                Uuidv7.generate(), objectId, revisionId, event,
                principal.authority().issuer(), principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(), at));
    }

    private static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireManage(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(KnowledgeAuthorities.MANAGE)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireRead(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(KnowledgeAuthorities.READ)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
