package dev.ainer.module.task.tasks.api;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.task.tasks.application.TaskApplicationService;
import dev.ainer.module.task.tasks.domain.TaskDefinition;
import dev.ainer.module.task.tasks.domain.TaskJob;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * 任务管理 API（ADR-0047）：注册类型、提交作业（延迟/周期）、管理面操作。
 * scope 在应用服务内强制；动作名词端点（cancellations/retries/status-changes）。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final TaskApplicationService service;

    public TaskController(
            AuthenticatedPrincipalResolver principalResolver,
            TaskApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    // ------------------------------------------------------------------ 传输模型

    public record RegisterDefinitionRequest(
            @NotBlank @Size(max = 128) String taskType,
            @NotBlank @Size(max = 256) String displayName,
            @NotBlank @Size(max = 256) String handlerRef,
            int maxAttempts,
            int timeoutSeconds) {
    }

    public record SubmitJobRequest(
            @NotBlank String taskType,
            String payload,
            @Nullable Long delaySeconds,
            @Nullable Long intervalSeconds) {
    }

    public record DefinitionResponse(
            UUID id, String taskType, String displayName, String handlerRef,
            int maxAttempts, int timeoutSeconds, String status) {

        static DefinitionResponse from(TaskDefinition d) {
            return new DefinitionResponse(d.id(), d.taskType(), d.displayName(),
                    d.handlerRef(), d.maxAttempts(), d.timeoutSeconds(), d.status());
        }
    }

    public record JobResponse(
            UUID id, String taskType, String status, int attemptCount, int maxAttempts,
            Instant nextRunAt, @Nullable Long intervalSeconds,
            @Nullable String lastError, Instant createdAt, @Nullable Instant completedAt) {

        static JobResponse from(TaskJob j) {
            return new JobResponse(j.id(), j.taskType(), j.status(),
                    j.attemptCount(), j.maxAttempts(), j.nextRunAt(), j.intervalSeconds(),
                    j.lastError(), j.createdAt(), j.completedAt());
        }
    }

    public record PageResponse<T>(List<T> records, long total, long page, long size) {
    }

    public record StatusChangeRequest(@NotBlank String status) {
    }

    // ------------------------------------------------------------------ 定义

    @PostMapping("/definitions")
    public ResponseEntity<ApiResponse<DefinitionResponse>> registerDefinition(
            @Valid @RequestBody RegisterDefinitionRequest body, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        DefinitionResponse response = DefinitionResponse.from(service.registerDefinition(
                principal, RequestIds.currentOrCreate(request),
                body.taskType(), body.displayName(), body.handlerRef(),
                body.maxAttempts(), body.timeoutSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/definitions")
    public ApiResponse<PageResponse<DefinitionResponse>> pageDefinitions(
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<DefinitionResponse> records = service.pageDefinitions(principal, page, size)
                .stream().map(DefinitionResponse::from).toList();
        long total = service.countDefinitions(principal);
        return ApiResponse.success(
                new PageResponse<>(records, total, Math.max(page, 1),
                        Math.min(Math.max(size, 1), 100)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/definitions/{taskType}/status-changes")
    public ApiResponse<DefinitionResponse> changeDefinitionStatus(
            @PathVariable("taskType") String taskType,
            @RequestBody StatusChangeRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(DefinitionResponse.from(service.changeDefinitionStatus(
                        principal, RequestIds.currentOrCreate(request), taskType, body.status())),
                RequestIds.currentOrCreate(request));
    }

    // ------------------------------------------------------------------ 作业

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<JobResponse>> submitJob(
            @RequestBody SubmitJobRequest body, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        JobResponse response = JobResponse.from(service.submitJob(
                principal, RequestIds.currentOrCreate(request),
                body.taskType(), body.payload(),
                body.delaySeconds(), body.intervalSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<JobResponse> getJob(
            @PathVariable("id") UUID id, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(JobResponse.from(service.getJob(principal, id)),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResponse<JobResponse>> pageJobs(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "taskType", required = false) String taskType,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<JobResponse> records = service.pageJobs(principal, status, taskType, page, size)
                .stream().map(JobResponse::from).toList();
        long total = service.countJobs(principal, status, taskType);
        return ApiResponse.success(
                new PageResponse<>(records, total, Math.max(page, 1),
                        Math.min(Math.max(size, 1), 100)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/jobs/{id}/cancellations")
    public ApiResponse<JobResponse> cancelJob(
            @PathVariable("id") UUID id, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(JobResponse.from(service.cancelJob(
                principal, RequestIds.currentOrCreate(request), id)),
                RequestIds.currentOrCreate(request));
    }

    @PostMapping("/jobs/{id}/retries")
    public ApiResponse<JobResponse> retryJob(
            @PathVariable("id") UUID id, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(JobResponse.from(service.retryJob(
                principal, RequestIds.currentOrCreate(request), id)),
                RequestIds.currentOrCreate(request));
    }
}
