package dev.ainer.core.error;

/**
 * 稳定、与传输协议无关的错误描述符。
 */
public interface ErrorCode {

    String code();

    String defaultMessage();

    int httpStatus();
}
