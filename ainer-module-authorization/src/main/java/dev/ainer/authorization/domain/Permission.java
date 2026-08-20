package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 授权权限定义（ADR-0030 §3）。一个稳定 code 加受控元数据。code 即身份：两个同 code
 * 但元数据不同的 Permission 是启动即失败的冲突。
 *
 * @param code            稳定权限 code
 * @param action          业务动作动词，例如 {@code read}/{@code publish}/{@code invoke}
 * @param resourceType    动作面向的资源类型
 * @param riskTier        风险层级，驱动 ALLOW 与 CHALLENGE 的分流
 * @param auditLevel      决策审计级别
 * @param systemOnly      仅系统/平台服务可持有或使用
 * @param agentDelegable  可进入 ADR-0031 的 ActingGrant；与 Role 可分配性正交
 */
public record Permission(
        PermissionCode code,
        String action,
        ResourceType resourceType,
        RiskTier riskTier,
        AuditLevel auditLevel,
        boolean systemOnly,
        boolean agentDelegable) {

    public Permission {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(riskTier, "riskTier");
        Objects.requireNonNull(auditLevel, "auditLevel");
        String normalizedAction = action.trim();
        if (normalizedAction.isEmpty()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        action = normalizedAction;
    }
}
