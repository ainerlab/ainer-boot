package dev.ainer.module.identity.account.application;

import java.util.Objects;

public record ProtectedTenantProvisioningNotification(
        String keyVersion,
        byte[] payload) {

    public ProtectedTenantProvisioningNotification {
        if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 32) {
            throw new IllegalArgumentException("Notification key version is invalid");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length < 32 || payload.length > 8192) {
            throw new IllegalArgumentException("Protected notification payload size is invalid");
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
