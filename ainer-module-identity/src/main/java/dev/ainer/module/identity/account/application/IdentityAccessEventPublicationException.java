package dev.ainer.module.identity.account.application;

import java.util.regex.Pattern;

public final class IdentityAccessEventPublicationException extends RuntimeException {

    private static final Pattern ERROR_CODE = Pattern.compile("AINER\\.[A-Z0-9_.]{1,89}");

    private final String errorCode;

    public IdentityAccessEventPublicationException(String errorCode) {
        this(errorCode, null);
    }

    public IdentityAccessEventPublicationException(String errorCode, Throwable cause) {
        super("Identity access event publication failed", cause);
        if (errorCode == null || !ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("Invalid access event publication error code");
        }
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
