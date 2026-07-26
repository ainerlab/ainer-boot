package dev.ainer.module.identity.account.application;

import java.time.Duration;
import java.util.Objects;

public record TenantProvisioningPolicy(
        Duration requestTtl,
        Duration activationTtl,
        int activationMaxAttempts) {

    private static final Duration MINIMUM_REQUEST_TTL = Duration.ofMinutes(15);
    private static final Duration MAXIMUM_REQUEST_TTL = Duration.ofDays(30);
    private static final Duration MINIMUM_ACTIVATION_TTL = Duration.ofMinutes(5);

    public TenantProvisioningPolicy {
        Objects.requireNonNull(requestTtl, "requestTtl");
        Objects.requireNonNull(activationTtl, "activationTtl");
        if (requestTtl.compareTo(MINIMUM_REQUEST_TTL) < 0
                || requestTtl.compareTo(MAXIMUM_REQUEST_TTL) > 0) {
            throw new IllegalArgumentException(
                    "Tenant provisioning request TTL is outside the supported range");
        }
        if (activationTtl.compareTo(MINIMUM_ACTIVATION_TTL) < 0
                || activationTtl.compareTo(requestTtl) > 0) {
            throw new IllegalArgumentException(
                    "Tenant activation TTL is outside the supported range");
        }
        if (activationMaxAttempts < 1 || activationMaxAttempts > 20) {
            throw new IllegalArgumentException(
                    "Tenant activation maximum attempts is outside the supported range");
        }
    }
}
