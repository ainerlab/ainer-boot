package dev.ainer.authorization.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** {@code ainer_authorization_acting_grant} 的行映射。 */
public class ActingGrantRow {

    private UUID id;

    private String principalIssuer;

    private String principalSubjectId;

    private String principalType;

    private UUID agentId;

    private String agentVersion;

    private String scopeKind;

    private UUID workspaceId;

    private String resourceType;

    private UUID resourceId;

    private Instant validFrom;

    private Instant validUntil;

    private String status;

    private Long version;



    public UUID getId() {
        return id;
    }


    public void setId(UUID id) {
        this.id = id;
    }


    public String getPrincipalIssuer() {
        return principalIssuer;
    }


    public void setPrincipalIssuer(String principalIssuer) {
        this.principalIssuer = principalIssuer;
    }


    public String getPrincipalSubjectId() {
        return principalSubjectId;
    }


    public void setPrincipalSubjectId(String principalSubjectId) {
        this.principalSubjectId = principalSubjectId;
    }


    public String getPrincipalType() {
        return principalType;
    }


    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }


    public UUID getAgentId() {
        return agentId;
    }


    public void setAgentId(UUID agentId) {
        this.agentId = agentId;
    }


    public String getAgentVersion() {
        return agentVersion;
    }


    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }


    public String getScopeKind() {
        return scopeKind;
    }


    public void setScopeKind(String scopeKind) {
        this.scopeKind = scopeKind;
    }


    public UUID getWorkspaceId() {
        return workspaceId;
    }


    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }


    public String getResourceType() {
        return resourceType;
    }


    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }


    public UUID getResourceId() {
        return resourceId;
    }


    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }


    public Instant getValidFrom() {
        return validFrom;
    }


    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }


    public Instant getValidUntil() {
        return validUntil;
    }


    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public Long getVersion() {
        return version;
    }


    public void setVersion(Long version) {
        this.version = version;
    }


}
