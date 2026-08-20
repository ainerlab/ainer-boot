package dev.ainer.authorization.api;

import dev.ainer.authorization.application.AuthorizationErrorCode;
import dev.ainer.authorization.application.GrantAdministrationGuard;
import dev.ainer.authorization.application.PermissionCatalogRepository;
import dev.ainer.authorization.application.RoleApplicationService;
import dev.ainer.authorization.application.RoleRepository;
import dev.ainer.authorization.application.SubjectBindingApplicationService;
import dev.ainer.authorization.application.SubjectBindingRepository;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.ainer.authorization.api.AuthorizationApiDtos.*;

/**
 * 授权模块的管理 REST API（ADR-0030 S2）。
 *
 * <p>所有端点都要求持有 {@code authorization.manage} scope 的 SERVICE 主体，且通过宿主
 * 带版本的 GrantAdministrationPolicy 的精确受信校验。守卫同时约束可分配的权限、scope
 * 与目标，并拒绝自我修改；仅拥有 scope 绝不等于拥有管理权。
 *
 * <p>变更类操作采用动作路径名词约定（{@code POST .../revocations}）而非物理 DELETE——
 * 撤销是逻辑状态迁移，不是行删除。
 */
@RestController
@RequestMapping("/api/authorization")
public class AuthorizationManagementController {

    private final RoleApplicationService roleService;
    private final SubjectBindingApplicationService bindingService;
    private final dev.ainer.authorization.application.SubjectSetBindingApplicationService setBindingService;
    private final dev.ainer.authorization.application.SubjectSetBindingRepository setBindingRepository;
    private final dev.ainer.authorization.application.ActingGrantApplicationService actingGrantService;
    private final RoleRepository roleRepository;
    private final SubjectBindingRepository bindingRepository;
    private final PermissionCatalogRepository permissionCatalogRepository;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final GrantAdministrationGuard administrationGuard;

    public AuthorizationManagementController(
            RoleApplicationService roleService,
            SubjectBindingApplicationService bindingService,
            RoleRepository roleRepository,
            SubjectBindingRepository bindingRepository,
            PermissionCatalogRepository permissionCatalogRepository,
            AuthenticatedPrincipalResolver principalResolver,
            GrantAdministrationGuard administrationGuard,
            dev.ainer.authorization.application.SubjectSetBindingApplicationService setBindingService,
            dev.ainer.authorization.application.SubjectSetBindingRepository setBindingRepository,
            dev.ainer.authorization.application.ActingGrantApplicationService actingGrantService) {
        this.roleService = roleService;
        this.bindingService = bindingService;
        this.setBindingService = setBindingService;
        this.setBindingRepository = setBindingRepository;
        this.actingGrantService = actingGrantService;
        this.roleRepository = roleRepository;
        this.bindingRepository = bindingRepository;
        this.permissionCatalogRepository = permissionCatalogRepository;
        this.principalResolver = principalResolver;
        this.administrationGuard = administrationGuard;
    }

    // ---- 权限目录（只读） ----

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> permissions(HttpServletRequest request) {
        requireManagement();
        List<PermissionResponse> items = permissionCatalogRepository.findAll().stream()
                .map(PermissionResponse::from)
                .toList();
        return ApiResponse.success(items, RequestIds.currentOrCreate(request));
    }

    // ---- Role 管理 ----

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @RequestBody CreateRoleRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        Set<PermissionCode> codes = parsePermissionCodes(body.permissions());
        String requestId = RequestIds.currentOrCreate(request);
        UUID roleId = roleService.createRole(principal, body.code(), body.name(), codes, requestId, null);
        RoleRepository.RoleRecord record = roleService.getRole(roleId);
        RoleResponse response = RoleResponse.from(record, codes.stream().map(PermissionCode::value)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/roles/{roleId}")
    public ApiResponse<RoleResponse> getRole(
            @PathVariable UUID roleId,
            HttpServletRequest request) {
        requireManagement();
        RoleRepository.RoleRecord record = roleService.getRole(roleId);
        Set<String> codes = record.role().permissions().stream()
                .map(PermissionCode::value)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return ApiResponse.success(RoleResponse.from(record, codes), RequestIds.currentOrCreate(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleResponse> replaceRolePermissions(
            @PathVariable UUID roleId,
            @RequestBody ReplaceRolePermissionsRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        RoleRepository.RoleRecord existing = roleService.getRole(roleId);
        Set<PermissionCode> codes = parsePermissionCodes(body.permissions());
        String requestId = RequestIds.currentOrCreate(request);
        roleService.replacePermissions(principal, roleId, codes, existing.version(), requestId, null);
        RoleRepository.RoleRecord reloaded = roleService.getRole(roleId);
        Set<String> codeStrings = reloaded.role().permissions().stream()
                .map(PermissionCode::value)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return ApiResponse.success(RoleResponse.from(reloaded, codeStrings), RequestIds.currentOrCreate(request));
    }

    // ---- Binding 管理 ----

    @PostMapping("/bindings")
    public ResponseEntity<ApiResponse<BindingResponse>> createBinding(
            @RequestBody CreateBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        SubjectRef subject = new SubjectRef(body.issuer(), body.subjectId(),
                SubjectType.valueOf(body.subjectType()));
        Scope scope = buildScope(body);
        String requestId = RequestIds.currentOrCreate(request);
        UUID bindingId = bindingService.createBinding(
                principal, subject, body.roleId(), scope, Instant.now(), body.validUntil(), requestId, null);
        SubjectBindingRepository.PersistedBinding pb = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BindingResponse.from(pb), RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/bindings/{bindingId}")
    public ApiResponse<BindingResponse> getBinding(
            @PathVariable UUID bindingId,
            HttpServletRequest request) {
        requireManagement();
        SubjectBindingRepository.PersistedBinding pb = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        return ApiResponse.success(BindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    @PostMapping("/bindings/{bindingId}/revocations")
    public ApiResponse<BindingResponse> revokeBinding(
            @PathVariable UUID bindingId,
            @RequestBody RevokeBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        String requestId = RequestIds.currentOrCreate(request);
        bindingService.revokeBinding(principal, bindingId, body.reason(), requestId, null);
        SubjectBindingRepository.PersistedBinding pb = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        return ApiResponse.success(BindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    // ---- 主体集合 Binding 管理（ADR-0042 O2） ----

    @PostMapping("/set-bindings")
    public ResponseEntity<ApiResponse<SetBindingResponse>> createSetBinding(
            @RequestBody CreateSetBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        dev.ainer.authorization.domain.SubjectSetRef set = new dev.ainer.authorization.domain.SubjectSetRef(
                body.setObjectType(), body.setObjectId(), body.setRelation(),
                body.setWorkspaceId(), body.setDirectoryId());
        Scope scope = buildScope(body.scopeKind(), body.workspaceId(), body.resourceType(), body.resourceId());
        String requestId = RequestIds.currentOrCreate(request);
        UUID bindingId = setBindingService.createSetBinding(
                principal, set, body.roleId(), scope, Instant.now(), body.validUntil(), requestId, null);
        dev.ainer.authorization.application.SubjectSetBindingRepository.PersistedSetBinding pb =
                setBindingRepository.findById(bindingId)
                        .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SetBindingResponse.from(pb), RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/set-bindings/{bindingId}")
    public ApiResponse<SetBindingResponse> getSetBinding(
            @PathVariable UUID bindingId,
            HttpServletRequest request) {
        requireManagement();
        dev.ainer.authorization.application.SubjectSetBindingRepository.PersistedSetBinding pb =
                setBindingRepository.findById(bindingId)
                        .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        return ApiResponse.success(SetBindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    @PostMapping("/set-bindings/{bindingId}/revocations")
    public ApiResponse<SetBindingResponse> revokeSetBinding(
            @PathVariable UUID bindingId,
            @RequestBody RevokeBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        String requestId = RequestIds.currentOrCreate(request);
        setBindingService.revokeSetBinding(principal, bindingId, body.reason(), requestId, null);
        dev.ainer.authorization.application.SubjectSetBindingRepository.PersistedSetBinding pb =
                setBindingRepository.findById(bindingId)
                        .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        return ApiResponse.success(SetBindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    // ---- ActingGrant 委托授权（ADR-0043 A1） ----

    @PostMapping("/acting-grants")
    public ResponseEntity<ApiResponse<ActingGrantResponse>> createActingGrant(
            @RequestBody CreateActingGrantRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        dev.ainer.authorization.domain.SubjectRef target = new dev.ainer.authorization.domain.SubjectRef(
                body.principalIssuer(), body.principalSubjectId(),
                dev.ainer.authorization.domain.SubjectType.valueOf(body.principalSubjectType()));
        java.util.Set<dev.ainer.authorization.domain.PermissionCode> permissions = new java.util.LinkedHashSet<>();
        for (String code : body.permissions()) {
            permissions.add(new dev.ainer.authorization.domain.PermissionCode(code));
        }
        Scope scope = buildScope(body.scopeKind(), body.workspaceId(), body.resourceType(),
                body.resourceId());
        String requestId = RequestIds.currentOrCreate(request);
        UUID grantId = actingGrantService.issueGrant(principal, target, body.agentId(),
                body.agentVersion(), permissions, scope, Instant.now(), body.validUntil(),
                requestId);
        dev.ainer.authorization.application.ActingGrantRepository.PersistedGrant grant =
                actingGrantService.findById(grantId)
                        .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.SET_BINDING_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ActingGrantResponse.from(grant),
                        RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/acting-grants/{grantId}")
    public ApiResponse<ActingGrantResponse> getActingGrant(
            @PathVariable UUID grantId, HttpServletRequest request) {
        requireManagement();
        return ApiResponse.success(ActingGrantResponse.from(actingGrantService.findById(grantId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ACTING_GRANT_NOT_FOUND))),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/acting-grants/{grantId}/revocations")
    public ApiResponse<ActingGrantResponse> revokeActingGrant(
            @PathVariable UUID grantId,
            @RequestBody RevokeBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement();
        actingGrantService.revokeGrant(principal, grantId, body.reason(),
                RequestIds.currentOrCreate(request));
        return ApiResponse.success(ActingGrantResponse.from(actingGrantService.findById(grantId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ACTING_GRANT_NOT_FOUND))),
                RequestIds.currentOrCreate(request));
    }

    // ---- Effective Access 查询 ----

    @GetMapping("/effective-access")
    public ApiResponse<EffectiveAccessResponse> effectiveAccess(
            @org.springframework.web.bind.annotation.RequestParam String issuer,
            @org.springframework.web.bind.annotation.RequestParam String subjectType,
            @org.springframework.web.bind.annotation.RequestParam String subjectId,
            HttpServletRequest request) {
        requireManagement();
        SubjectRef subject = new SubjectRef(issuer, subjectId, SubjectType.valueOf(subjectType));
        List<SubjectBindingRepository.PersistedBinding> bindings = bindingService.liveBindings(subject);
        EffectiveAccessResponse response = EffectiveAccessResponse.from(issuer, subjectType, subjectId, bindings);
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    // ---- 辅助方法 ----

    private AuthenticatedPrincipal requireManagement() {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        administrationGuard.requireManager(principal);
        return principal;
    }

    private static Set<PermissionCode> parsePermissionCodes(Set<String> codes) {
        Set<PermissionCode> result = new LinkedHashSet<>();
        for (String code : codes) {
            result.add(new PermissionCode(code));
        }
        return result;
    }

    private static Scope buildScope(CreateBindingRequest body) {
        return buildScope(body.scopeKind(), body.workspaceId(), body.resourceType(), body.resourceId());
    }

    /** 保留 resourceType：内部子集校验合成锚点使用，不得经 API 声明（防伪造绑定绕过）。 */
    private static final java.util.Set<String> RESERVED_RESOURCE_TYPES = java.util.Set.of(
            "workspace.anchor", "request");

    private static Scope buildScope(
            String scopeKind, java.util.UUID workspaceId, String resourceType, java.util.UUID resourceId) {
        return switch (scopeKind) {
            case "GLOBAL" -> new Scope.Global();
            case "WORKSPACE" -> {
                if (workspaceId == null) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Workspace(workspaceId);
            }
            case "RESOURCE" -> {
                if (workspaceId == null || resourceType == null || resourceId == null
                        || RESERVED_RESOURCE_TYPES.contains(resourceType)) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Resource(workspaceId, new ResourceType(resourceType), resourceId);
            }
            default -> throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
        };
    }
}
