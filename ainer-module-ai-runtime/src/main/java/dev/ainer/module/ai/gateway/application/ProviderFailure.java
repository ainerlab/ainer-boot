package dev.ainer.module.ai.gateway.application;

/**
 * 模型提供方失败异常：按 Kind 分类（限流/超时/不可用/协议错误），
 * 由应用服务映射为对应的 {@link AiGatewayErrorCode}；不携带供应商响应正文。
 */
public final class ProviderFailure extends RuntimeException {

    public enum Kind {
        RATE_LIMITED,
        TIMEOUT,
        UNAVAILABLE,
        PROTOCOL
    }

    private final Kind kind;

    public ProviderFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ProviderFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
