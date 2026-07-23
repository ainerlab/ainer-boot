package dev.ainer.module.workspace.workspace.api;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.workspace.workspace.application.AddWorkspaceMemberCommand;
import dev.ainer.module.workspace.workspace.application.CreateWorkspaceCommand;
import dev.ainer.module.workspace.workspace.application.ChangeWorkspaceMemberRoleCommand;
import dev.ainer.module.workspace.workspace.application.RemoveWorkspaceMemberCommand;
import dev.ainer.module.workspace.workspace.application.TransferWorkspaceOwnershipCommand;
import dev.ainer.module.workspace.workspace.application.WorkspaceApplicationService;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceApplicationService service;
    private final AuthenticatedActorResolver actorResolver;

    public WorkspaceController(
            WorkspaceApplicationService service,
            AuthenticatedActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @Valid @RequestBody CreateWorkspaceRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actorResolver.requireCurrent();
        WorkspaceResponse response = WorkspaceResponse.from(service.create(
                actor, new CreateWorkspaceCommand(request.name())));
        return ResponseEntity.created(URI.create("/api/workspaces/" + response.id()))
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(servletRequest)));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkspaceResponse> get(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceResponse.from(service.get(actorResolver.requireCurrent(), id)),
                RequestIds.currentOrCreate(servletRequest));
    }

    @GetMapping
    public ApiResponse<WorkspacePageResponse> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspacePageResponse.from(service.page(actorResolver.requireCurrent(), page, size)),
                RequestIds.currentOrCreate(servletRequest));
    }

    @GetMapping("/{id}/authorization-audits")
    public ApiResponse<WorkspaceAuthorizationAuditPageResponse> authorizationAudits(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceAuthorizationAuditPageResponse.from(service.authorizationAudits(
                        actorResolver.requireCurrent(), id, page, size)),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WorkspaceResponse> rename(
            @PathVariable UUID id,
            @Valid @RequestBody RenameWorkspaceRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceResponse.from(service.rename(actorResolver.requireCurrent(), id, request.name())),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddWorkspaceMemberRequest request,
            HttpServletRequest servletRequest) {
        WorkspaceMemberResponse response = WorkspaceMemberResponse.from(service.addMember(
                actorResolver.requireCurrent(),
                id,
                new AddWorkspaceMemberCommand(request.subjectId(), request.role())));
        return ResponseEntity.status(201)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(servletRequest)));
    }

    @PostMapping("/{id}/membership-acceptances")
    public ApiResponse<WorkspaceMemberResponse> acceptMembership(
            @PathVariable UUID id,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceMemberResponse.from(service.acceptInvitation(
                        actorResolver.requireCurrent(), id)),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{id}/member-role-changes")
    public ApiResponse<WorkspaceMemberResponse> changeMemberRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeWorkspaceMemberRoleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceMemberResponse.from(service.changeMemberRole(
                        actorResolver.requireCurrent(),
                        id,
                        new ChangeWorkspaceMemberRoleCommand(request.subjectId(), request.role()))),
                RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{id}/member-removals")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID id,
            @Valid @RequestBody RemoveWorkspaceMemberRequest request,
            HttpServletRequest servletRequest) {
        service.removeMember(
                actorResolver.requireCurrent(),
                id,
                new RemoveWorkspaceMemberCommand(request.subjectId()));
        return ApiResponse.success(null, RequestIds.currentOrCreate(servletRequest));
    }

    @PostMapping("/{id}/ownership-transfers")
    public ApiResponse<WorkspaceMemberResponse> transferOwnership(
            @PathVariable UUID id,
            @Valid @RequestBody TransferWorkspaceOwnershipRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                WorkspaceMemberResponse.from(service.transferOwnership(
                        actorResolver.requireCurrent(),
                        id,
                        new TransferWorkspaceOwnershipCommand(request.newOwnerSubjectId()))),
                RequestIds.currentOrCreate(servletRequest));
    }
}
