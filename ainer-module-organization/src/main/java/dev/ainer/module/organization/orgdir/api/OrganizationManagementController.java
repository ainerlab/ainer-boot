package dev.ainer.module.organization.orgdir.api;

import dev.ainer.authorization.spring.AinerAuthorize;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.AssignPositionRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.AssignUnitRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.CreateDirectoryRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.CreatePositionRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.CreateUnitRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.DirectoryResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.EngageRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.EngagementResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.PageResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.PositionAssignmentResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.PositionResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.TransferRequest;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.UnitAssignmentResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.UnitMemberResponse;
import dev.ainer.module.organization.orgdir.api.OrganizationApiDtos.UnitResponse;
import dev.ainer.module.organization.orgdir.application.DirectoryApplicationService;
import dev.ainer.module.organization.orgdir.application.OrganizationAuthorities;
import dev.ainer.module.organization.orgdir.application.WorkforceApplicationService;
import dev.ainer.module.organization.orgdir.domain.AssignmentKind;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组织目录管理 API（ADR-0042 O1）：命令式端点（create/transfer/suspend/terminate），分页
 * ≤100，scope 在应用服务内对已验证 principal 强制。参考装配另有 {@code @AinerAuthorize}
 * 粗门禁（需 Binding）；模块 HTTP 切片未装配拦截器时注解不生效。请求体/查询里的
 * {@code workspaceId} 不作为授权目标输入。
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationManagementController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final DirectoryApplicationService directoryService;
    private final WorkforceApplicationService workforceService;

    public OrganizationManagementController(
            AuthenticatedPrincipalResolver principalResolver,
            DirectoryApplicationService directoryService,
            WorkforceApplicationService workforceService) {
        this.principalResolver = principalResolver;
        this.directoryService = directoryService;
        this.workforceService = workforceService;
    }

    @PostMapping("/directories")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<DirectoryResponse>> createDirectory(
            @RequestBody CreateDirectoryRequest body, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        DirectoryResponse response = DirectoryResponse.from(directoryService.createDirectory(
                principal, RequestIds.currentOrCreate(request), body.workspaceId(),
                body.code(), body.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/directories")
    @AinerAuthorize(permission = OrganizationAuthorities.READ)
    public ApiResponse<PageResponse<DirectoryResponse>> pageDirectories(
            @RequestParam("workspaceId") UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<DirectoryResponse> records = directoryService
                .pageDirectories(principal, workspaceId, page, size).stream()
                .map(DirectoryResponse::from).toList();
        long total = directoryService.countDirectories(principal, workspaceId);
        return ApiResponse.success(
                new PageResponse<>(records, total, Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/directories/{directoryId}/units")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(
            @PathVariable("directoryId") UUID directoryId,
            @RequestBody CreateUnitRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UnitResponse response = UnitResponse.from(directoryService.createUnit(
                principal, RequestIds.currentOrCreate(request), directoryId,
                body.parentUnitId(), body.code(), body.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/directories/{directoryId}/units")
    @AinerAuthorize(permission = OrganizationAuthorities.READ)
    public ApiResponse<List<UnitResponse>> unitTree(
            @PathVariable("directoryId") UUID directoryId, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<UnitResponse> units = directoryService.unitTree(principal, directoryId).stream()
                .map(UnitResponse::from).toList();
        return ApiResponse.success(units, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/directories/{directoryId}/engagements")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<EngagementResponse>> engage(
            @PathVariable("directoryId") UUID directoryId,
            @RequestBody EngageRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        EngagementResponse response = EngagementResponse.from(workforceService.engage(
                principal, RequestIds.currentOrCreate(request), directoryId,
                body.subjectIssuer(), body.subjectId(), body.engagementType(),
                body.employeeNumber(), body.validFrom(), body.validUntil()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/directories/{directoryId}/engagements")
    @AinerAuthorize(permission = OrganizationAuthorities.READ)
    public ApiResponse<PageResponse<EngagementResponse>> pageEngagements(
            @PathVariable("directoryId") UUID directoryId,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<EngagementResponse> records = workforceService
                .pageEngagements(principal, directoryId, page, size).stream()
                .map(EngagementResponse::from).toList();
        long total = workforceService.countEngagements(principal, directoryId);
        return ApiResponse.success(
                new PageResponse<>(records, total, Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/directories/{directoryId}/engagements/{engagementId}/suspensions")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ApiResponse<EngagementResponse> suspendEngagement(
            @PathVariable("directoryId") UUID directoryId,
            @PathVariable("engagementId") UUID engagementId,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(EngagementResponse.from(workforceService.suspendEngagement(
                principal, RequestIds.currentOrCreate(request), directoryId, engagementId)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/directories/{directoryId}/engagements/{engagementId}/terminations")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ApiResponse<EngagementResponse> terminateEngagement(
            @PathVariable("directoryId") UUID directoryId,
            @PathVariable("engagementId") UUID engagementId,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(EngagementResponse.from(workforceService.terminateEngagement(
                principal, RequestIds.currentOrCreate(request), directoryId, engagementId)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/directories/{directoryId}/unit-assignments")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<UnitAssignmentResponse>> assignUnit(
            @PathVariable("directoryId") UUID directoryId,
            @RequestBody AssignUnitRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UnitAssignmentResponse response = UnitAssignmentResponse.from(
                workforceService.assignUnit(principal, RequestIds.currentOrCreate(request),
                        directoryId, body.engagementId(), body.orgUnitId(),
                        parseKind(body.kind()), body.validFrom(), body.validUntil()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @PostMapping("/directories/{directoryId}/unit-assignments/{assignmentId}/transfers")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<UnitAssignmentResponse>> transferUnitAssignment(
            @PathVariable("directoryId") UUID directoryId,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestBody TransferRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UnitAssignmentResponse response = UnitAssignmentResponse.from(
                workforceService.transferUnitAssignment(principal,
                        RequestIds.currentOrCreate(request), directoryId, body.engagementId(),
                        assignmentId, body.targetUnitId(), body.atTime()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @PostMapping("/directories/{directoryId}/positions")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<PositionResponse>> createPosition(
            @PathVariable("directoryId") UUID directoryId,
            @RequestBody CreatePositionRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        PositionResponse response = PositionResponse.from(workforceService.createPosition(
                principal, RequestIds.currentOrCreate(request), directoryId, body.orgUnitId(),
                body.code(), body.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @PostMapping("/directories/{directoryId}/position-assignments")
    @AinerAuthorize(permission = OrganizationAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<PositionAssignmentResponse>> assignPosition(
            @PathVariable("directoryId") UUID directoryId,
            @RequestBody AssignPositionRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        PositionAssignmentResponse response = PositionAssignmentResponse.from(
                workforceService.assignPosition(principal, RequestIds.currentOrCreate(request),
                        directoryId, body.positionId(), body.engagementId(),
                        body.unitAssignmentId(), parseKind(body.kind()), body.validFrom(),
                        body.validUntil()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    /** Unit 成员投影（决策时实时解析，ADR-0042 §3）。 */
    @GetMapping("/directories/{directoryId}/units/{unitId}/members")
    @AinerAuthorize(permission = OrganizationAuthorities.READ)
    public ApiResponse<List<UnitMemberResponse>> unitMembers(
            @PathVariable("directoryId") UUID directoryId,
            @PathVariable("unitId") UUID unitId,
            @RequestParam(name = "atTime", required = false) Instant atTime,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<UnitAssignment> assignments = workforceService
                .unitMembers(principal, directoryId, unitId, atTime);
        Map<UUID, WorkforceEngagement> engagements = workforceService
                .engagementsForMembers(principal, assignments.stream()
                        .map(UnitAssignment::engagementId).distinct().toList())
                .stream().collect(Collectors.toMap(WorkforceEngagement::id, Function.identity()));
        List<UnitMemberResponse> members = assignments.stream()
                .map(assignment -> UnitMemberResponse.from(
                        assignment, engagements.get(assignment.engagementId())))
                .toList();
        return ApiResponse.success(members, RequestIds.currentOrCreate(request));
    }

    @GetMapping("/directories/{directoryId}/positions/{positionId}/assignees")
    @AinerAuthorize(permission = OrganizationAuthorities.READ)
    public ApiResponse<List<PositionAssignmentResponse>> positionAssignees(
            @PathVariable("directoryId") UUID directoryId,
            @PathVariable("positionId") UUID positionId,
            @RequestParam(name = "atTime", required = false) Instant atTime,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<PositionAssignmentResponse> assignees = workforceService
                .positionAssignees(principal, directoryId, positionId, atTime).stream()
                .map(PositionAssignmentResponse::from).toList();
        return ApiResponse.success(assignees, RequestIds.currentOrCreate(request));
    }

    private static AssignmentKind parseKind(String kind) {
        try {
            return AssignmentKind.valueOf(kind);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST, "kind 不合法");
        }
    }
}
