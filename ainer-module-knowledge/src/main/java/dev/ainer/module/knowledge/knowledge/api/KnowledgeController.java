package dev.ainer.module.knowledge.knowledge.api;

import dev.ainer.authorization.spring.AinerAuthorize;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.knowledge.knowledge.application.KnowledgeApplicationService;
import dev.ainer.module.knowledge.knowledge.application.KnowledgeAuthorities;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeObject;
import dev.ainer.module.knowledge.knowledge.domain.KnowledgeRevision;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Knowledge 管理 API（ADR-0044 K1/K2）：提案/发布（人工门禁）/supersede/asOf 解析。
 * 动作名词端点（publications）；scope 在应用服务内强制。参考装配另有
 * {@code @AinerAuthorize} 粗门禁（需 Binding）；模块切片未装配拦截器时注解不生效。
 * 请求体/查询里的 {@code workspaceId} 不作为授权目标输入。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final KnowledgeApplicationService service;

    public KnowledgeController(
            AuthenticatedPrincipalResolver principalResolver,
            KnowledgeApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    public record CreateObjectRequest(UUID workspaceId, String kind, String title) {
    }

    public record SourceDto(String sourceType, String sourceRef) {
    }

    public record EvidenceDto(String linkType, String targetRef) {
    }

    public record ProposeRevisionRequest(
            String payloadMarkdown,
            List<SourceDto> sources,
            List<EvidenceDto> evidence,
            @Nullable UUID basedOnRevisionId) {
    }

    public record ObjectResponse(UUID id, UUID workspaceId, String kind, String title,
            String status, Instant createdAt) {

        static ObjectResponse from(KnowledgeObject object) {
            return new ObjectResponse(object.id(), object.workspaceId(), object.kind(),
                    object.title(), object.status(), object.createdAt());
        }
    }

    public record RevisionResponse(
            UUID id, UUID objectId, long revisionNumber, String status, Instant createdAt,
            @Nullable Instant publishedAt, String createdByType, String createdById,
            String payloadMarkdown,
            List<SourceDto> sources, List<EvidenceDto> evidence) {

        static RevisionResponse from(KnowledgeRevision revision) {
            return new RevisionResponse(revision.id(), revision.objectId(),
                    revision.revisionNumber(), revision.status(), revision.createdAt(),
                    revision.publishedAt(), revision.createdByType(), revision.createdById(),
                    revision.payloadMarkdown(),
                    revision.sources().stream()
                            .map(source -> new SourceDto(source.sourceType(), source.sourceRef()))
                            .toList(),
                    revision.evidence().stream()
                            .map(link -> new EvidenceDto(link.linkType(), link.targetRef()))
                            .toList());
        }
    }

    public record PageResponse<T>(List<T> records, long total, long page, long size) {
    }

    @PostMapping("/objects")
    @AinerAuthorize(permission = KnowledgeAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<ObjectResponse>> createObject(
            @RequestBody CreateObjectRequest body, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        ObjectResponse response = ObjectResponse.from(service.createObject(
                principal, RequestIds.currentOrCreate(request), body.workspaceId(),
                body.kind(), body.title()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/objects")
    @AinerAuthorize(permission = KnowledgeAuthorities.READ)
    public ApiResponse<PageResponse<ObjectResponse>> pageObjects(
            @RequestParam("workspaceId") UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<ObjectResponse> records = service.pageObjects(principal, workspaceId, page, size)
                .stream().map(ObjectResponse::from).toList();
        long total = service.countObjects(principal, workspaceId);
        return ApiResponse.success(
                new PageResponse<>(records, total, Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/objects/{objectId}/revisions")
    @AinerAuthorize(permission = KnowledgeAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<RevisionResponse>> proposeRevision(
            @PathVariable("objectId") UUID objectId,
            @RequestBody ProposeRevisionRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        RevisionResponse response = RevisionResponse.from(service.proposeRevision(
                principal, RequestIds.currentOrCreate(request), objectId,
                body.payloadMarkdown(),
                body.sources() == null ? List.of() : body.sources().stream()
                        .map(source -> new dev.ainer.module.knowledge.knowledge.domain.KnowledgeSource(
                                null, null, source.sourceType(), source.sourceRef())).toList(),
                body.evidence() == null ? List.of() : body.evidence().stream()
                        .map(link -> new dev.ainer.module.knowledge.knowledge.domain.KnowledgeEvidence(
                                null, null, link.linkType(), link.targetRef())).toList(),
                body.basedOnRevisionId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    /** 人工发布门禁：SERVICE/AI 调用一律 403（ADR-0044 不变式 #9）。 */
    @PostMapping("/revisions/{revisionId}/publications")
    @AinerAuthorize(permission = KnowledgeAuthorities.MANAGE)
    public ApiResponse<RevisionResponse> publishRevision(
            @PathVariable("revisionId") UUID revisionId, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(RevisionResponse.from(service.publishRevision(
                principal, RequestIds.currentOrCreate(request), revisionId)),
                RequestIds.currentOrCreate(request));
    }

    /** asOf 解析：返回该时刻已发布的精确 Revision pin。 */
    @GetMapping("/objects/{objectId}")
    @AinerAuthorize(permission = KnowledgeAuthorities.READ)
    public ApiResponse<RevisionResponse> resolveObject(
            @PathVariable("objectId") UUID objectId,
            @RequestParam(name = "asOf", required = false) Instant asOf,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(RevisionResponse.from(
                service.resolveObject(principal, objectId, asOf)),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/revisions/{revisionId}")
    @AinerAuthorize(permission = KnowledgeAuthorities.READ)
    public ApiResponse<RevisionResponse> getRevision(
            @PathVariable("revisionId") UUID revisionId, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(RevisionResponse.from(
                service.getRevision(principal, revisionId)),
                RequestIds.currentOrCreate(request));
    }
}
