package dev.ainer.module.identity.account.application;

import java.util.List;

public record PlatformIdentityTenantPage(
        List<PlatformIdentityTenantProjection> items,
        int page,
        int size,
        long total) {

    public PlatformIdentityTenantPage {
        items = List.copyOf(items);
    }
}
