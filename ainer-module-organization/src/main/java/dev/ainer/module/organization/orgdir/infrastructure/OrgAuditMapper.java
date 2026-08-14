package dev.ainer.module.organization.orgdir.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface OrgAuditMapper {

    void insert(AuditRow row);

    List<AuditRow> selectByEntity(@Param("entityType") String entityType,
            @Param("entityId") UUID entityId, @Param("limit") int limit);

    long countByEntity(@Param("entityId") UUID entityId);
}
