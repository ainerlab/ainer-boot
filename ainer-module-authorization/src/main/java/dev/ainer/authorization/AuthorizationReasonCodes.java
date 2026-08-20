package dev.ainer.authorization;

import dev.ainer.authorization.domain.ReasonCode;

/**
 * {@link dev.ainer.authorization.domain.AuthorizationDecision} 使用的稳定、低基数 reason code
 * 集合（ADR-0030 §6.1）。这些 code 不得向匿名/非成员调用方泄露资源存在性或策略内部细节；
 * HTTP 适配器会将其映射为安全的外层错误码。
 */
public final class AuthorizationReasonCodes {

    public static final ReasonCode PUBLIC_ALLOWED = new ReasonCode("PUBLIC_ALLOWED");
    public static final ReasonCode NO_PUBLIC_POLICY = new ReasonCode("NO_PUBLIC_POLICY");
    public static final ReasonCode AUTHENTICATED_REQUIRED = new ReasonCode("AUTHENTICATED_REQUIRED");
    public static final ReasonCode UNKNOWN_PERMISSION = new ReasonCode("UNKNOWN_PERMISSION");
    public static final ReasonCode SCOPE_CEILING = new ReasonCode("SCOPE_CEILING");
    public static final ReasonCode WORKSPACE_CEILING = new ReasonCode("WORKSPACE_CEILING");
    public static final ReasonCode NO_BINDING = new ReasonCode("NO_BINDING");
    public static final ReasonCode NO_RELATION = new ReasonCode("NO_RELATION");
    public static final ReasonCode STATE_DENIED = new ReasonCode("STATE_DENIED");
    public static final ReasonCode RESOURCE_TYPE_MISMATCH = new ReasonCode("RESOURCE_TYPE_MISMATCH");
    public static final ReasonCode SYSTEM_ONLY = new ReasonCode("SYSTEM_ONLY");
    public static final ReasonCode UNKNOWN_POLICY = new ReasonCode("UNKNOWN_POLICY");
    public static final ReasonCode STRONG_AUTH_REQUIRED = new ReasonCode("STRONG_AUTH_REQUIRED");
    public static final ReasonCode AUTHORIZED = new ReasonCode("AUTHORIZED");
    public static final ReasonCode UNEXPECTED = new ReasonCode("UNEXPECTED");

    private AuthorizationReasonCodes() {
    }
}
