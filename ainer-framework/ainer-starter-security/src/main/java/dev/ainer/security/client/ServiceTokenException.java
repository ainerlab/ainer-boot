package dev.ainer.security.client;

public final class ServiceTokenException extends RuntimeException {

    public ServiceTokenException(String message) {
        super(message);
    }

    public ServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
