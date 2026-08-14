package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SubjectSetBindingMapper {

    int insert(@Param("id") UUID id, @Param("setObjectType") String setObjectType,
            @Param("setObjectId") UUID setObjectId, @Param("setRelation") String setRelation,
            @Param("setWorkspaceId") UUID setWorkspaceId, @Param("setDirectoryId") UUID setDirectoryId,
            @Param("roleId") UUID roleId, @Param("scopeKind") String scopeKind,
            @Param("workspaceId") UUID workspaceId, @Param("resourceType") String resourceType,
            @Param("resourceId") UUID resourceId, @Param("validFrom") Instant validFrom,
            @Param("validUntil") Instant validUntil, @Param("now") Instant now);

    SetBindingRow selectById(@Param("id") UUID id);

    int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason, @Param("now") Instant now);

    List<SetBindingRow> selectLive(@Param("resource") ResourceFilter resource, @Param("at") Instant at);

    /** SQL-side resource filter parameters for live set-binding queries. */
    class ResourceFilter {
        private final java.util.UUID workspaceId;
        private final String resourceType;
        private final java.util.UUID resourceId;

        public ResourceFilter(java.util.UUID workspaceId, String resourceType, java.util.UUID resourceId) {
            this.workspaceId = workspaceId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        public java.util.UUID getWorkspaceId() {
            return workspaceId;
        }

        public String getResourceType() {
            return resourceType;
        }

        public java.util.UUID getResourceId() {
            return resourceId;
        }
    }
}
