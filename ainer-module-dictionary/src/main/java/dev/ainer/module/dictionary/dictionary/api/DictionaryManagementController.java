package dev.ainer.module.dictionary.dictionary.api;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.dictionary.dictionary.application.DictionaryApplicationService;
import dev.ainer.module.dictionary.dictionary.application.DictionaryErrorCode;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dictionary management API (ADR-0040). Scopes ({@code dictionary.read} / {@code dictionary.manage})
 * are enforced in the application service against the verified principal; every mutation writes a
 * same-transaction audit row.
 */
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryManagementController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final DictionaryApplicationService service;

    public DictionaryManagementController(
            AuthenticatedPrincipalResolver principalResolver,
            DictionaryApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    // ---- Types ----

    @PostMapping("/types")
    public ResponseEntity<ApiResponse<DictionaryApiDtos.DictionaryTypeResponse>> createType(
            @RequestBody DictionaryApiDtos.CreateTypeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UUID id = service.createType(principal, RequestIds.currentOrCreate(request),
                body.parentId(), body.code(), body.name(), body.nameEn(), body.description());
        DictionaryApiDtos.DictionaryTypeResponse response = service.getType(principal, id)
                .map(DictionaryApiDtos.DictionaryTypeResponse::from)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/types")
    public ApiResponse<DictionaryApiDtos.DictionaryTypePageResponse> pageTypes(
            @RequestParam(value = "status", required = false) @Nullable String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryTypePageResponse.from(
                        service.pageTypes(principal, status, page, size), page, size),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/types/{id}")
    public ApiResponse<DictionaryApiDtos.DictionaryTypeResponse> getType(
            @PathVariable UUID id, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                service.getType(principal, id)
                        .map(DictionaryApiDtos.DictionaryTypeResponse::from)
                        .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND)),
                RequestIds.currentOrCreate(request));
    }

    @PutMapping("/types/{id}")
    public ApiResponse<DictionaryApiDtos.DictionaryTypeResponse> updateType(
            @PathVariable UUID id,
            @RequestBody DictionaryApiDtos.UpdateTypeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        DictionaryType updated = service.updateType(principal, RequestIds.currentOrCreate(request),
                id, body.name(), body.nameEn(), body.description(), body.sortIndex(),
                body.expectedVersion());
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryTypeResponse.from(updated),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/types/{id}/status-changes")
    public ApiResponse<DictionaryApiDtos.DictionaryTypeResponse> changeTypeStatus(
            @PathVariable UUID id,
            @RequestBody DictionaryApiDtos.StatusChangeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        DictionaryType updated = service.changeTypeStatus(
                principal, RequestIds.currentOrCreate(request), id,
                parseStatus(body.status()), body.expectedVersion());
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryTypeResponse.from(updated),
                RequestIds.currentOrCreate(request));
    }

    // ---- Items ----

    @PostMapping("/types/{typeId}/items")
    public ResponseEntity<ApiResponse<DictionaryApiDtos.DictionaryItemResponse>> createItem(
            @PathVariable UUID typeId,
            @RequestBody DictionaryApiDtos.CreateItemRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        UUID id = service.createItem(principal, RequestIds.currentOrCreate(request), typeId,
                body.code(), body.label(), body.labelEn(), body.value(),
                body.sortIndex() == null ? 0 : body.sortIndex(), body.cssClass(), body.remark());
        DictionaryApiDtos.DictionaryItemResponse response = service.getItem(principal, id)
                .map(DictionaryApiDtos.DictionaryItemResponse::from)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.ITEM_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/types/{typeId}/items")
    public ApiResponse<DictionaryApiDtos.DictionaryItemPageResponse> pageItems(
            @PathVariable UUID typeId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryItemPageResponse.from(
                        service.pageItems(principal, typeId, page, size), page, size),
                RequestIds.currentOrCreate(request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<DictionaryApiDtos.DictionaryItemResponse> updateItem(
            @PathVariable UUID id,
            @RequestBody DictionaryApiDtos.UpdateItemRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryItemResponse.from(
                        service.updateItem(principal, RequestIds.currentOrCreate(request), id,
                                body.label(), body.labelEn(), body.value(), body.sortIndex(),
                                body.cssClass(), body.remark(), body.expectedVersion())),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/items/{id}/status-changes")
    public ApiResponse<DictionaryApiDtos.DictionaryItemResponse> changeItemStatus(
            @PathVariable UUID id,
            @RequestBody DictionaryApiDtos.StatusChangeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                DictionaryApiDtos.DictionaryItemResponse.from(
                        service.changeItemStatus(principal, RequestIds.currentOrCreate(request),
                                id, parseStatus(body.status()), body.expectedVersion())),
                RequestIds.currentOrCreate(request));
    }

    private static DictionaryStatus parseStatus(String status) {
        try {
            return DictionaryStatus.valueOf(status.strip().toUpperCase());
        } catch (RuntimeException exception) {
            throw new BusinessException(DictionaryErrorCode.INVALID_REQUEST);
        }
    }
}
