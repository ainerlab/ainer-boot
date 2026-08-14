package dev.ainer.module.notification.notification.application;

import java.util.List;

/** Generic pagination slice for notification queries (items + total). */
public record NotificationPageSlice<T>(List<T> items, long total) {

    public NotificationPageSlice {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
    }
}
