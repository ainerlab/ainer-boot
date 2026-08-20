package dev.ainer.module.notification.notification.application;

import java.util.List;

/** 通知查询的通用分页切片（items + total）。 */
public record NotificationPageSlice<T>(List<T> items, long total) {

    public NotificationPageSlice {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
    }
}
