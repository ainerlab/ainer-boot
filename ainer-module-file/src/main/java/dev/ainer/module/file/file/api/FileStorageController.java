package dev.ainer.module.file.file.api;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.file.file.application.FileStorageApplicationService;
import dev.ainer.module.file.file.domain.FileObject;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * File storage management API (ADR-0040). Upload is multipart; download streams bytes with the
 * original filename via {@code Content-Disposition}. Scopes ({@code file.read} / {@code file.write})
 * are enforced in the application service against the verified principal.
 */
@RestController
@RequestMapping("/api/files")
public class FileStorageController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final FileStorageApplicationService service;

    public FileStorageController(
            AuthenticatedPrincipalResolver principalResolver,
            FileStorageApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @RequestParam("namespace") String namespace,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        FileResponse response;
        try (InputStream content = file.getInputStream()) {
            FileObject object = service.upload(
                    principal,
                    RequestIds.currentOrCreate(request),
                    namespace,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content);
            response = FileResponse.from(object);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping
    public ApiResponse<FilePageResponse> page(
            @RequestParam(value = "namespace", required = false) @Nullable String namespace,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(
                FilePageResponse.from(service.page(principal, namespace, page, size)),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        FileStorageApplicationService.DownloadedFile downloaded = service.download(principal, id);
        FileObject object = downloaded.object();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(object.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(safeMediaType(object.contentType()))
                .body(new InputStreamResource(downloaded.content()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        service.delete(principal, RequestIds.currentOrCreate(request), id);
        return ApiResponse.success(null, RequestIds.currentOrCreate(request));
    }

    /** Stored content types passed the upload allow-list; parse defensively regardless. */
    private static MediaType safeMediaType(@Nullable String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
