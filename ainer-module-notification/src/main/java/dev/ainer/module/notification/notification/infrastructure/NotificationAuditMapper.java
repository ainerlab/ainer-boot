package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for {@code ainer_notification_audit}; SQL in {@code mapper/notification/NotificationAuditMapper.xml}. */
@Mapper
public interface NotificationAuditMapper {

    int insert(@Param("row") NotificationAuditRow row);
}
