package dev.ainer.module.identity.account.application;

import java.util.Objects;

public record TenantProvisioningCompletion(
        boolean activated,
        TenantProvisioningRequest request,
        ProvisionedIdentity identity,
        IdentityErrorCode failure) {

    public TenantProvisioningCompletion {
        Objects.requireNonNull(request, "request");
        if (activated) {
            Objects.requireNonNull(identity, "identity");
            if (failure != null) {
                throw new IllegalArgumentException("Successful provisioning cannot contain a failure");
            }
        } else if (failure == null || identity != null) {
            throw new IllegalArgumentException("Failed provisioning must contain only a failure");
        }
    }

    public static TenantProvisioningCompletion activated(
            TenantProvisioningRequest request,
            ProvisionedIdentity identity) {
        return new TenantProvisioningCompletion(true, request, identity, null);
    }

    public static TenantProvisioningCompletion rejected(
            TenantProvisioningRequest request,
            IdentityErrorCode failure) {
        return new TenantProvisioningCompletion(false, request, null, failure);
    }
}
