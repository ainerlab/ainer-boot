package dev.ainer.module.identity.account.application;

import java.util.List;

public record IdentityAccessEventOutboxPage(
        List<IdentityAccessEventOutboxEntry> items,
        int page,
        int size,
        long total) {

    public IdentityAccessEventOutboxPage {
        items = List.copyOf(items);
    }
}
