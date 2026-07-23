package dev.ainer.module.ai.gateway.application;

import dev.ainer.core.error.ErrorCode;

public enum AiGatewayErrorCode implements ErrorCode {
    INVALID_REQUEST("AINER.AI.INVALID_REQUEST", "AI 调用请求不合法", 400),
    INVALID_CONTEXT("AINER.AI.INVALID_CONTEXT", "调用上下文不合法", 400),
    MODEL_NOT_ALLOWED("AINER.AI.MODEL_NOT_ALLOWED", "模型不在允许列表中", 422),
    PROMPT_TOO_LARGE("AINER.AI.PROMPT_TOO_LARGE", "提示内容超过允许大小", 413),
    SENSITIVE_DATA_REJECTED("AINER.AI.SENSITIVE_DATA_REJECTED", "请求包含禁止发送的敏感信息", 422),
    RATE_LIMITED("AINER.AI.RATE_LIMITED", "租户调用频率超过限制", 429),
    BUDGET_EXCEEDED("AINER.AI.BUDGET_EXCEEDED", "租户当日 AI 预算不足", 429),
    PROVIDER_RATE_LIMITED("AINER.AI.PROVIDER_RATE_LIMITED", "模型服务暂时限流", 503),
    PROVIDER_TIMEOUT("AINER.AI.PROVIDER_TIMEOUT", "模型服务调用超时", 504),
    PROVIDER_UNAVAILABLE("AINER.AI.PROVIDER_UNAVAILABLE", "模型服务当前不可用", 503),
    PROVIDER_PROTOCOL_ERROR("AINER.AI.PROVIDER_PROTOCOL_ERROR", "模型服务返回了无效响应", 502),
    INVOCATION_NOT_FOUND("AINER.AI.INVOCATION_NOT_FOUND", "AI 调用记录不存在", 404);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AiGatewayErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
