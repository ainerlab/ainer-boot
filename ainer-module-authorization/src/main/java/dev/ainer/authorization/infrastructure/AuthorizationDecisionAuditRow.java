package dev.ainer.authorization.infrastructure;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code ainer_authorization_decision_audit} 的行映射。
 */
public class AuthorizationDecisionAuditRow {

    private UUID decisionId;
    private @Nullable UUID workspaceId;
    private String requesterIssuer;
    private String requesterType;
    private String requesterId;
    private String permissionCode;
    private @Nullable String resourceType;
    private @Nullable UUID resourceId;
    private String outcome;
    private String reasonCode;
    private String policyVersion;
    private @Nullable String requestId;
    private @Nullable String traceId;
    private Instant evaluatedAt;

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }

    public @Nullable UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getRequesterIssuer() { return requesterIssuer; }
    public void setRequesterIssuer(String requesterIssuer) { this.requesterIssuer = requesterIssuer; }

    public String getRequesterType() { return requesterType; }
    public void setRequesterType(String requesterType) { this.requesterType = requesterType; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public @Nullable String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public @Nullable UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    public @Nullable String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public @Nullable String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
