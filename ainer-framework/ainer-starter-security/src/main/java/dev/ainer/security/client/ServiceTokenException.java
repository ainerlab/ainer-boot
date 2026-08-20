package dev.ainer.security.client;

/** 服务 Token 获取失败异常：携带稳定消息，不包含 token 端点响应正文。 */
public final class ServiceTokenException extends RuntimeException {

    public ServiceTokenException(String message) {
        super(message);
    }

    public ServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
