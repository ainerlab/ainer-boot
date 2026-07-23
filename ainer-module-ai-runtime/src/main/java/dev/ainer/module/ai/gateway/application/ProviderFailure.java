package dev.ainer.module.ai.gateway.application;

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
