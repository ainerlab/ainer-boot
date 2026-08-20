package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全链失败响应写出器：在认证/授权被拒绝时（401/403/503）以
 * {@link ApiResponse#failure} 信封写出稳定错误码、默认消息与 requestId，
 * 与业务错误响应保持同一响应契约。
 */
public final class AinerSecurityFailureWriter {

    private final ObjectMapper objectMapper;

    public AinerSecurityFailureWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode, errorCode.defaultMessage(), RequestIds.currentOrCreate(request)));
    }
}
