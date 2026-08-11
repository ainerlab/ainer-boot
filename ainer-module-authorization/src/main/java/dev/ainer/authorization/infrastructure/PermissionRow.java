package dev.ainer.authorization.infrastructure;

import java.time.Instant;

/**
 * Row mapping for {@code ainer_authorization_permission}.
 */
public class PermissionRow {

    private String code;
    private String action;
    private String resourceType;
    private String riskTier;
    private String auditLevel;
    private boolean systemOnly;
    private boolean agentDelegable;
    private String sourceModule;
    private int definitionVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }

    public String getAuditLevel() { return auditLevel; }
    public void setAuditLevel(String auditLevel) { this.auditLevel = auditLevel; }

    public boolean isSystemOnly() { return systemOnly; }
    public void setSystemOnly(boolean systemOnly) { this.systemOnly = systemOnly; }

    public boolean isAgentDelegable() { return agentDelegable; }
    public void setAgentDelegable(boolean agentDelegable) { this.agentDelegable = agentDelegable; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public int getDefinitionVersion() { return definitionVersion; }
    public void setDefinitionVersion(int definitionVersion) { this.definitionVersion = definitionVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
