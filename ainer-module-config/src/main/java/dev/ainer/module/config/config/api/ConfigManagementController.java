package dev.ainer.module.config.config.api;

import dev.ainer.authorization.spring.AinerAuthorize;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.config.config.application.ConfigAuthorities;
import dev.ainer.module.config.config.application.ConfigApplicationService;
import dev.ainer.module.config.config.application.ConfigErrorCode;
import dev.ainer.module.config.config.domain.ConfigValueType;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置管理 API（ADR-0040）。写入要求 {@code config.manage}，读取要求
 * {@code config.read}；secret 明文只接受一次且绝不回显——历史记录存储
 * {@code [encrypted]} 占位而非明文。参考装配另有 {@code @AinerAuthorize} 粗门禁
 * （需 Binding）；模块切片未装配拦截器时注解不生效。
 */
@RestController
@RequestMapping("/api/configs")
public class ConfigManagementController {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final ConfigApplicationService service;

    public ConfigManagementController(
            AuthenticatedPrincipalResolver principalResolver,
            ConfigApplicationService service) {
        this.principalResolver = principalResolver;
        this.service = service;
    }

    @PostMapping("/entries")
    @AinerAuthorize(permission = ConfigAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<Void>> setValue(
            @RequestBody ConfigApiDtos.SetValueRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        service.setValue(body.namespace(), body.key(), body.value(),
                parseType(body.valueType()), body.description(), principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null, RequestIds.currentOrCreate(request)));
    }

    @PostMapping("/secrets")
    @AinerAuthorize(permission = ConfigAuthorities.MANAGE)
    public ResponseEntity<ApiResponse<Void>> setSecret(
            @RequestBody ConfigApiDtos.SetSecretRequest body,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        service.setSecret(body.namespace(), body.key(), body.plaintext(),
                parseType(body.valueType()), body.description(), principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null, RequestIds.currentOrCreate(request)));
    }

    @GetMapping("/entries")
    @AinerAuthorize(permission = ConfigAuthorities.READ)
    public ApiResponse<ConfigApiDtos.ConfigEntryListResponse> listEntries(
            @RequestParam("namespace") String namespace,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<ConfigApiDtos.ConfigEntryResponse> items = service
                .getByNamespace(principal, requireNamespace(namespace)).stream()
                .map(ConfigApiDtos.ConfigEntryResponse::from)
                .toList();
        return ApiResponse.success(
                new ConfigApiDtos.ConfigEntryListResponse(items),
                RequestIds.currentOrCreate(request));
    }

    @GetMapping("/history")
    @AinerAuthorize(permission = ConfigAuthorities.READ)
    public ApiResponse<ConfigApiDtos.ConfigHistoryListResponse> history(
            @RequestParam("namespace") String namespace,
            @RequestParam("key") String key,
            HttpServletRequest request) {
        AuthenticatedPrincipal principal = principalResolver.requireCurrent();
        List<ConfigApiDtos.ConfigHistoryResponse> items = service
                .getHistory(principal, requireNamespace(namespace), key.strip()).stream()
                .map(ConfigApiDtos.ConfigHistoryResponse::from)
                .toList();
        return ApiResponse.success(
                new ConfigApiDtos.ConfigHistoryListResponse(items),
                RequestIds.currentOrCreate(request));
    }

    private static String requireNamespace(@Nullable String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new BusinessException(ConfigErrorCode.INVALID_REQUEST);
        }
        return namespace.strip();
    }

    private static ConfigValueType parseType(String valueType) {
        try {
            return ConfigValueType.valueOf(valueType.strip().toUpperCase());
        } catch (RuntimeException exception) {
            throw new BusinessException(ConfigErrorCode.INVALID_REQUEST);
        }
    }
}
