package dev.ainer.authorization.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row for {@code ainer_authorization_subject_set_binding}. */
public class SetBindingRow {

    private UUID id;
    private String setObjectType;
    private UUID setObjectId;
    private String setRelation;
    private UUID setWorkspaceId;
    private UUID setDirectoryId;
    private UUID roleId;
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

    public String getSetObjectType() {
        return setObjectType;
    }

    public void setSetObjectType(String setObjectType) {
        this.setObjectType = setObjectType;
    }

    public UUID getSetObjectId() {
        return setObjectId;
    }

    public void setSetObjectId(UUID setObjectId) {
        this.setObjectId = setObjectId;
    }

    public String getSetRelation() {
        return setRelation;
    }

    public void setSetRelation(String setRelation) {
        this.setRelation = setRelation;
    }

    public UUID getSetWorkspaceId() {
        return setWorkspaceId;
    }

    public void setSetWorkspaceId(UUID setWorkspaceId) {
        this.setWorkspaceId = setWorkspaceId;
    }

    public UUID getSetDirectoryId() {
        return setDirectoryId;
    }

    public void setSetDirectoryId(UUID setDirectoryId) {
        this.setDirectoryId = setDirectoryId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
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
