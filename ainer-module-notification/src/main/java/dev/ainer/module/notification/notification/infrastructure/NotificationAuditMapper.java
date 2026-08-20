package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code ainer_notification_audit} 的 MyBatis mapper；SQL 位于 {@code mapper/notification/NotificationAuditMapper.xml}。 */
@Mapper
public interface NotificationAuditMapper {

    int insert(@Param("row") NotificationAuditRow row);
}
