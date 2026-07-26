package dev.ainer.module.identity.account.application;

import java.util.List;

public record PlatformIdentityUserPage(
        List<PlatformIdentityUserProjection> items,
        int page,
        int size,
        long total) {

    public PlatformIdentityUserPage {
        items = List.copyOf(items);
    }
}
