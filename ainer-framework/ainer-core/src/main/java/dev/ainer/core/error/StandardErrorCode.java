package dev.ainer.core.error;

/**
 * 所有 Ainer 应用共享的错误码。
 */
public enum StandardErrorCode implements ErrorCode {

    OK("AINER.COMMON.OK", "OK", 200),
    INVALID_REQUEST("AINER.COMMON.INVALID_REQUEST", "请求参数不正确", 400),
    UNAUTHENTICATED("AINER.COMMON.UNAUTHENTICATED", "请先完成身份认证", 401),
    FORBIDDEN("AINER.COMMON.FORBIDDEN", "无权执行该操作", 403),
    NOT_FOUND("AINER.COMMON.NOT_FOUND", "请求的资源不存在", 404),
    CONFLICT("AINER.COMMON.CONFLICT", "资源状态冲突", 409),
    RATE_LIMITED("AINER.COMMON.RATE_LIMITED", "请求过于频繁，请稍后再试", 429),
    BUSINESS_RULE_VIOLATION("AINER.COMMON.BUSINESS_RULE_VIOLATION", "业务规则校验失败", 422),
    INTERNAL_ERROR("AINER.COMMON.INTERNAL_ERROR", "服务暂时不可用", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    StandardErrorCode(String code, String defaultMessage, int httpStatus) {
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
