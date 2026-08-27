package {{package.name}}.{{entity.package}}.api;

import {{package.name}}.{{entity.package}}.application.{{entity.className}}ApplicationService;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/{{resource.path}}")
public class {{entity.className}}Controller {

    private final {{entity.className}}ApplicationService service;
    private final AuthenticatedPrincipalResolver principalResolver;

    public {{entity.className}}Controller(
            {{entity.className}}ApplicationService service,
            AuthenticatedPrincipalResolver principalResolver) {
        this.service = service;
        this.principalResolver = principalResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<{{entity.className}}ApiDtos.Response>> create(
            @PathVariable("workspaceId") UUID workspaceId,
            @Valid @RequestBody {{entity.className}}ApiDtos.CreateRequest request,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        var created = service.create(
                principalResolver.requireCurrent(), workspaceId, request.toCommand(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success({{entity.className}}ApiDtos.Response.from(created), requestId));
    }

    @GetMapping("/{id}")
    public ApiResponse<{{entity.className}}ApiDtos.Response> get(
            @PathVariable("workspaceId") UUID workspaceId,
            @PathVariable("id") UUID id,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        var found = service.get(principalResolver.requireCurrent(), workspaceId, id, requestId);
        return ApiResponse.success({{entity.className}}ApiDtos.Response.from(found), requestId);
    }

    @GetMapping
    public ApiResponse<{{entity.className}}ApiDtos.PageResponse> page(
            @PathVariable("workspaceId") UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        var result = service.page(
                principalResolver.requireCurrent(), workspaceId, page, size, requestId);
        return ApiResponse.success({{entity.className}}ApiDtos.PageResponse.from(result), requestId);
    }

    @PutMapping("/{id}")
    public ApiResponse<{{entity.className}}ApiDtos.Response> update(
            @PathVariable("workspaceId") UUID workspaceId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody {{entity.className}}ApiDtos.UpdateRequest request,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        var updated = service.update(
                principalResolver.requireCurrent(), workspaceId, id, request.toCommand(), requestId);
        return ApiResponse.success({{entity.className}}ApiDtos.Response.from(updated), requestId);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("workspaceId") UUID workspaceId,
            @PathVariable("id") UUID id,
            @RequestParam(name = "version") @PositiveOrZero long version,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        service.delete(principalResolver.requireCurrent(), workspaceId, id, version, requestId);
        return ApiResponse.success(null, requestId);
    }
}
