package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface NotificationTemplateMapper {
    UUID insertReturningId(@Param("row") NotificationTemplateRow row, @Param("now") Instant now);
    NotificationTemplateRow selectActiveByCode(@Param("code") String code);
    NotificationTemplateRow selectById(@Param("id") UUID id);
    int update(@Param("id") UUID id, @Param("titleTemplate") @Nullable String titleTemplate,
            @Param("bodyTemplate") @Nullable String bodyTemplate,
            @Param("variablesSchema") @Nullable String variablesSchema,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    int updateStatus(@Param("id") UUID id, @Param("status") String status,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    List<NotificationTemplateRow> selectPage(@Nullable @Param("status") String status,
            @Param("offset") long offset, @Param("limit") int limit);
    long countPage(@Nullable @Param("status") String status);
}
