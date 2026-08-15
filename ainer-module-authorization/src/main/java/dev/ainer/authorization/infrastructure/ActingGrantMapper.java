package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ActingGrantMapper {

    int insertGrant(@Param("id") UUID id, @Param("issuer") String issuer,
            @Param("subjectId") String subjectId, @Param("agentId") UUID agentId,
            @Param("agentVersion") String agentVersion, @Param("scopeKind") String scopeKind,
            @Param("workspaceId") UUID workspaceId, @Param("resourceType") String resourceType,
            @Param("resourceId") UUID resourceId, @Param("validFrom") Instant validFrom,
            @Param("validUntil") Instant validUntil, @Param("now") Instant now);

    int insertGrantPermissions(@Param("grantId") UUID grantId, @Param("codes") List<String> codes);

    ActingGrantRow selectById(@Param("id") UUID id);

    int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason, @Param("now") Instant now);

    List<ActingGrantRow> selectLiveByPrincipal(@Param("issuer") String issuer,
            @Param("subjectId") String subjectId, @Param("at") Instant at);

    List<String> selectPermissions(@Param("grantId") UUID grantId);
}
