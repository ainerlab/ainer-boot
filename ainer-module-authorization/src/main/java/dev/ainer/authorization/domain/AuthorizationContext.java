package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 类型化的、已验证的授权上下文（ADR-0030 §5.5）。只提供显式字段；不存在任意
 * {@code Map<String,Object>}、SpEL、Rego、SQL 或管理员上传的规则。
 *
 * @param evaluatedAt    决策时间（同时用于 Binding 有效性检查）
 * @param assurance      当前认证保证强度
 * @param platformAppId  已验证的平台应用/渠道上下文，可为空
 * @param requestId      关联 id，可为空
 * @param traceId        追踪 id，可为空
 */
public record AuthorizationContext(
        Instant evaluatedAt,
        Assurance assurance,
        @Nullable String platformAppId,
        @Nullable String requestId,
        @Nullable String traceId) {

    public AuthorizationContext {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(assurance, "assurance");
    }

    /** 认证保证强度（ADR-0030 §6.3）。 */
    public enum Assurance {
        NONE,
        RECENT_STRONG
    }
}
