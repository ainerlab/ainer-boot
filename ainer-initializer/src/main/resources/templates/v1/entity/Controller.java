package {{package.name}}.crud;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * CRUD endpoint for {@code {{table.name}}} (manifest v1, ADR-0036). Responses use the
 * {@link ApiResponse} envelope and carry the request trace id; security-aware wiring
 * is intentionally left to the consumer application.
 */
@RestController
@RequestMapping("/api/{{resource.path}}")
public class {{entity.className}}Controller {

    private final {{entity.className}}ApplicationService service;

    public {{entity.className}}Controller({{entity.className}}ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<{{entity.className}}Entity>> create(
            @RequestBody {{entity.className}}Entity row, HttpServletRequest request) {
        {{entity.className}}Entity created = service.create(row);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<{{entity.className}}Entity> get(
            @PathVariable("id") UUID id, HttpServletRequest request) {
        return ApiResponse.success(service.get(id), RequestIds.currentOrCreate(request));
    }

    @GetMapping
    public ApiResponse<IPage<{{entity.className}}Entity>> page(
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return ApiResponse.success(service.page(page, size), RequestIds.currentOrCreate(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<{{entity.className}}Entity> update(
            @PathVariable("id") UUID id,
            @RequestBody {{entity.className}}Entity changes,
            HttpServletRequest request) {
        return ApiResponse.success(service.update(id, changes), RequestIds.currentOrCreate(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") UUID id, HttpServletRequest request) {
        service.delete(id);
        return ApiResponse.success(null, RequestIds.currentOrCreate(request));
    }
}