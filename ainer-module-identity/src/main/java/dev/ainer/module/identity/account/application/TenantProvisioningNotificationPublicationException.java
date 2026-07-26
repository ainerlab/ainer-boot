package dev.ainer.module.identity.account.application;

public class TenantProvisioningNotificationPublicationException extends RuntimeException {

    private final String errorCode;

    public TenantProvisioningNotificationPublicationException(
            String errorCode,
            String message) {
        super(message);
        this.errorCode = requireErrorCode(errorCode);
    }

    public TenantProvisioningNotificationPublicationException(
            String errorCode,
            Throwable cause) {
        super("Tenant provisioning notification publication failed", cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    private static String requireErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("^AINER\\.[A-Z0-9_.]{1,89}$")) {
            throw new IllegalArgumentException("Notification publication error code is invalid");
        }
        return errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
