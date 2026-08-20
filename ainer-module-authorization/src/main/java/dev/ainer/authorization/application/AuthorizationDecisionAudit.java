package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.AuthorizationOutcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单次授权决策的 append-only 记录（ADR-0030 §12.4）。按触发权限的
 * {@code Permission.auditLevel} 决定是否写入——并非每次读取都审计。不保存 Token、
 * prompt、资源正文或 PII；只保存稳定身份引用、权限、结果、reason code 与追踪 id。
 *
 * @param decisionId      {@code AuthorizationDecision.decisionId}（UUIDv7，时间有序）
 * @param workspaceId     workspace 上下文，可为 null
 * @param requesterIssuer 请求者 issuer 命名空间
 * @param requesterType   {@code USER} 或 {@code SERVICE}
 * @param requesterId     请求者 subject id
 * @param permissionCode  被求值的权限
 * @param resourceType    资源类型，可为 null
 * @param resourceId      资源 id，可为 null
 * @param outcome         {@code ALLOW}、{@code DENY} 或 {@code CHALLENGE}
 * @param reasonCode      稳定的低基数 reason code
 * @param policyVersion   决策携带的策略版本标签
 * @param requestId       请求追踪 id，可为 null
 * @param traceId         分布式追踪 id，可为 null
 * @param evaluatedAt     决策求值时间
 */
public record AuthorizationDecisionAudit(
        UUID decisionId,
        UUID workspaceId,
        String requesterIssuer,
        String requesterType,
        String requesterId,
        String permissionCode,
        String resourceType,
        UUID resourceId,
        AuthorizationOutcome outcome,
        String reasonCode,
        String policyVersion,
        String requestId,
        String traceId,
        Instant evaluatedAt) {

    public AuthorizationDecisionAudit {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(requesterIssuer, "requesterIssuer");
        Objects.requireNonNull(requesterType, "requesterType");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(permissionCode, "permissionCode");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }
}
