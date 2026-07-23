package dev.ainer.core.error;

/**
 * Stable, transport-neutral error descriptor.
 */
public interface ErrorCode {

    String code();

    String defaultMessage();

    int httpStatus();
}
