package dev.ainer.module.ai.agent.api;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.ai.agent.application.AiAgentApplicationService;
import dev.ainer.module.ai.agent.domain.AiAgentDefinition;
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

import java.util.List;
import java.util.UUID;

/** Agent 定义管理 API（ADR-0043 A1）：注册/退役/查询；scope 在应用服务内强制。 */
@RestController
@RequestMapping("/api/ai/agents")
public class AiAgentController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final AiAgentApplicationService service;

    public AiAgentController(
            AuthenticatedPrincipalResolver principalResolver, AiAgentApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    public record RegisterRequest(String code, String version, String purpose,
            @Nullable String runtimeRef, @Nullable UUID workspaceId) {
    }

    public record AgentResponse(UUID id, String code, String version, String status,
            String purpose, @Nullable String runtimeRef, @Nullable UUID workspaceId) {

        public static AgentResponse from(AiAgentDefinition agent) {
            return new AgentResponse(agent.id(), agent.code(), agent.version(), agent.status(),
                    agent.purpose(), agent.runtimeRef(), agent.workspaceId());
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> register(
            @RequestBody RegisterRequest body, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        AgentResponse response = AgentResponse.from(service.register(principal, body.code(),
                body.version(), body.purpose(), body.runtimeRef(), body.workspaceId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, RequestIds.currentOrCreate(request)));
    }

    @PostMapping("/{agentId}/retirements")
    public ApiResponse<AgentResponse> retire(
            @PathVariable("agentId") UUID agentId, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(AgentResponse.from(service.retire(principal, agentId)),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/{agentId}")
    public ApiResponse<AgentResponse> get(
            @PathVariable("agentId") UUID agentId, HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        return ApiResponse.success(AgentResponse.from(service.get(principal, agentId)),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping
    public ApiResponse<List<AgentResponse>> page(
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<AgentResponse> agents = service.page(principal, page, size).stream()
                .map(AgentResponse::from).toList();
        return ApiResponse.success(agents, RequestIds.currentOrCreate(request));
    }
}
