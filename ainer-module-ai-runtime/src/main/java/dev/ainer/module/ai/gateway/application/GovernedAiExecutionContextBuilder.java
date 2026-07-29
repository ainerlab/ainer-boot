package dev.ainer.module.ai.gateway.application;

import java.util.Set;
import java.util.UUID;

/**
 * {@link GovernedAiExecutionContext} 的可变构建器，用于在调用链中逐步补充字段。
 *
 * <p>典型用法：
 * <pre>
 * GovernedAiExecutionContext ctx = resolver.resolve(actor, requestId)
 *         .withPurpose("weekly-report")
 *         .withTaskType("identity-summary");
 * </pre>
 */
public final class GovernedAiExecutionContextBuilder {

    private UUID tenantId;
    private UUID workspaceId;
    private String actorType;
    private String actorId;
    private UUID memberId;
    private UUID identityId;
    private UUID identityVersionId;
    private String purpose;
    private String taskType;
    private Set<String> scopes;
    private String dataScope;
    private String dataClassification;
    private String entitlementPolicyVersion;
    private String retentionPolicy;
    private String traceId;
    private String requestId;

    public static GovernedAiExecutionContextBuilder from(GovernedAiExecutionContext ctx) {
        GovernedAiExecutionContextBuilder b = new GovernedAiExecutionContextBuilder();
        b.tenantId = ctx.tenantId();
        b.workspaceId = ctx.workspaceId();
        b.actorType = ctx.actorType();
        b.actorId = ctx.actorId();
        b.memberId = ctx.memberId();
        b.identityId = ctx.identityId();
        b.identityVersionId = ctx.identityVersionId();
        b.purpose = ctx.purpose();
        b.taskType = ctx.taskType();
        b.scopes = ctx.scopes();
        b.dataScope = ctx.dataScope();
        b.dataClassification = ctx.dataClassification();
        b.entitlementPolicyVersion = ctx.entitlementPolicyVersion();
        b.retentionPolicy = ctx.retentionPolicy();
        b.traceId = ctx.traceId();
        b.requestId = ctx.requestId();
        return b;
    }

    public GovernedAiExecutionContextBuilder workspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    public GovernedAiExecutionContextBuilder memberId(UUID memberId) {
        this.memberId = memberId;
        return this;
    }

    public GovernedAiExecutionContextBuilder identityId(UUID identityId) {
        this.identityId = identityId;
        return this;
    }

    public GovernedAiExecutionContextBuilder purpose(String purpose) {
        this.purpose = purpose;
        return this;
    }

    public GovernedAiExecutionContextBuilder taskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public GovernedAiExecutionContextBuilder dataScope(String dataScope) {
        this.dataScope = dataScope;
        return this;
    }

    public GovernedAiExecutionContextBuilder entitlementPolicyVersion(String version) {
        this.entitlementPolicyVersion = version;
        return this;
    }

    public GovernedAiExecutionContext build() {
        return new GovernedAiExecutionContext(
                tenantId, workspaceId, actorType, actorId, memberId,
                identityId, identityVersionId, purpose, taskType, scopes,
                dataScope, dataClassification, entitlementPolicyVersion,
                retentionPolicy, traceId, requestId);
    }
}
