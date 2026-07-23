package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxPage;

import java.util.List;

public record IdentityAccessEventOutboxPageResponse(
        List<IdentityAccessEventOutboxResponse> items,
        int page,
        int size,
        long total) {

    static IdentityAccessEventOutboxPageResponse from(IdentityAccessEventOutboxPage page) {
        return new IdentityAccessEventOutboxPageResponse(
                page.items().stream().map(IdentityAccessEventOutboxResponse::from).toList(),
                page.page(), page.size(), page.total());
    }
}
