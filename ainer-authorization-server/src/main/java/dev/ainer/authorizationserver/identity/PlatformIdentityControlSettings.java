package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.TenantProvisioningPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record PlatformIdentityControlSettings(
        Set<String> operatorClientIds,
        TenantProvisioningPolicy policy,
        String notificationProtectionActiveKeyVersion,
        Map<String, byte[]> notificationProtectionKeys) {

    public PlatformIdentityControlSettings {
        operatorClientIds = Set.copyOf(operatorClientIds);
        notificationProtectionKeys = deepCopy(notificationProtectionKeys);
    }

    @Override
    public Map<String, byte[]> notificationProtectionKeys() {
        return deepCopy(notificationProtectionKeys);
    }

    private static Map<String, byte[]> deepCopy(Map<String, byte[]> source) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((version, key) -> copy.put(version, key.clone()));
        return Map.copyOf(copy);
    }
}
