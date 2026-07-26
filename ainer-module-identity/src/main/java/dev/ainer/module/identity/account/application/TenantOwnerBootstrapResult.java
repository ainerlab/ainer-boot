package dev.ainer.module.identity.account.application;

import java.util.Objects;

public record TenantOwnerBootstrapResult(ProvisionedIdentity identity, boolean created) {

    public TenantOwnerBootstrapResult {
        Objects.requireNonNull(identity, "identity");
    }
}
