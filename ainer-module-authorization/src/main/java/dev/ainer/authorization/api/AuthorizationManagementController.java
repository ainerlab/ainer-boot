package dev.ainer.authorization.api;

import dev.ainer.authorization.application.AuthorizationErrorCode;
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
 * Management REST API for the authorization module (ADR-0030 S2).
 *
 * <p>All endpoints require a service principal with {@code authorization.manage} scope. Human principals
 * are rejected — authorization management is a platform operation, not an end-user action.
 * Resource-level authorization (e.g. workspace-scoped binding management) will be layered on top of
 * these application services in S3.
 *
 * <p>Mutations use the action-path noun convention ({@code POST .../revocations}) rather than physical
 * DELETE — revocation is a logical state transition, not row deletion.
 */
@RestController
@RequestMapping("/api/authorization")
public class AuthorizationManagementController {

    static final String MANAGE_SCOPE = "authorization.manage";

    private final RoleApplicationService roleService;
    private final SubjectBindingApplicationService bindingService;
    private final RoleRepository roleRepository;
    private final SubjectBindingRepository bindingRepository;
    private final PermissionCatalogRepository permissionCatalogRepository;
    private final AuthenticatedPrincipalResolver principalResolver;

    public AuthorizationManagementController(
            RoleApplicationService roleService,
            SubjectBindingApplicationService bindingService,
            RoleRepository roleRepository,
            SubjectBindingRepository bindingRepository,
            PermissionCatalogRepository permissionCatalogRepository,
            AuthenticatedPrincipalResolver principalResolver) {
        this.roleService = roleService;
        this.bindingService = bindingService;
        this.roleRepository = roleRepository;
        this.bindingRepository = bindingRepository;
        this.permissionCatalogRepository = permissionCatalogRepository;
        this.principalResolver = principalResolver;
    }

    // ---- Permission catalog (read-only) ----

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> permissions(HttpServletRequest request) {
        requireManagement(principalResolver);
        List<PermissionResponse> items = permissionCatalogRepository.findAll().stream()
                .map(PermissionResponse::from)
                .toList();
        return ApiResponse.success(items, RequestIds.currentOrCreate(request));
    }

    // ---- Role management ----

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @RequestBody CreateRoleRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement(principalResolver);
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
        requireManagement(principalResolver);
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
        AuthenticatedPrincipal principal = requireManagement(principalResolver);
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

    // ---- Binding management ----

    @PostMapping("/bindings")
    public ResponseEntity<ApiResponse<BindingResponse>> createBinding(
            @RequestBody CreateBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement(principalResolver);
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
        requireManagement(principalResolver);
        SubjectBindingRepository.PersistedBinding pb = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        return ApiResponse.success(BindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    @PostMapping("/bindings/{bindingId}/revocations")
    public ApiResponse<BindingResponse> revokeBinding(
            @PathVariable UUID bindingId,
            @RequestBody RevokeBindingRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = requireManagement(principalResolver);
        String requestId = RequestIds.currentOrCreate(request);
        bindingService.revokeBinding(principal, bindingId, body.reason(), requestId, null);
        SubjectBindingRepository.PersistedBinding pb = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.BINDING_NOT_FOUND));
        return ApiResponse.success(BindingResponse.from(pb), RequestIds.currentOrCreate(request));
    }

    // ---- Effective Access ----

    @GetMapping("/effective-access")
    public ApiResponse<EffectiveAccessResponse> effectiveAccess(
            @org.springframework.web.bind.annotation.RequestParam String issuer,
            @org.springframework.web.bind.annotation.RequestParam String subjectType,
            @org.springframework.web.bind.annotation.RequestParam String subjectId,
            HttpServletRequest request) {
        requireManagement(principalResolver);
        SubjectRef subject = new SubjectRef(issuer, subjectId, SubjectType.valueOf(subjectType));
        List<SubjectBindingRepository.PersistedBinding> bindings = bindingService.liveBindings(subject);
        EffectiveAccessResponse response = EffectiveAccessResponse.from(issuer, subjectType, subjectId, bindings);
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    // ---- Helpers ----

    private static AuthenticatedPrincipal requireManagement(AuthenticatedPrincipalResolver resolver) {
        AuthenticatedPrincipal principal = resolver.requireCurrent();
        if (!principal.isService()) {
            throw new BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
        }
        if (!principal.hasScope(MANAGE_SCOPE)) {
            throw new BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
        }
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
        return switch (body.scopeKind()) {
            case "GLOBAL" -> new Scope.Global();
            case "WORKSPACE" -> {
                if (body.workspaceId() == null) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Workspace(body.workspaceId());
            }
            case "RESOURCE" -> {
                if (body.workspaceId() == null || body.resourceType() == null || body.resourceId() == null) {
                    throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
                }
                yield new Scope.Resource(body.workspaceId(), new ResourceType(body.resourceType()), body.resourceId());
            }
            default -> throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
        };
    }
}
