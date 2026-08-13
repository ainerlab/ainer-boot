package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

@Mapper
public interface NotificationTemplateMapper {
    UUID insertReturningId(@Param("row") NotificationTemplateRow row, @Param("now") Instant now);
    NotificationTemplateRow selectActiveByCode(@Param("code") String code);
}
