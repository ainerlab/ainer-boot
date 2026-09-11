package dev.ainer.authorizationserver.identitycontrol;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.foundation.AccountStatus;
import dev.ainer.module.identity.foundation.CredentialType;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipal;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 身份生命周期控制面的应用服务：人员账号与服务主体的状态变更、密码轮换与凭据撤销。
 *
 * <p>每个用例都是**一个事务**：{@code security_epoch} 递增、状态/凭据写入与安全操作审计
 * 一起提交或一起回滚。epoch 递增本身由 {@code IdentityFoundationService} 以带期望态的条件
 * UPDATE 完成（compare-and-set），因此并发迁移只有一个能成功，另一个得到 409 而不是覆盖。
 *
 * <p>调用方必须是配置中精确登记的 SERVICE 主体：不仅要求已验证的 SERVICE Token 与最小
 * scope，还要求该 ServicePrincipal 当前仍为 ACTIVE 且 Token 的 {@code sec_epoch} 等于其当前
 * epoch——服务主体被禁用/轮换后，旧 Token 即使未过期也不能再操作身份控制面。
 */
@Service
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.identity-control",
        name = "enabled",
        havingValue = "true")
public class IdentityControlService {

    static final String HUMAN_ACCOUNT = "HUMAN_ACCOUNT";
    static final String SERVICE_PRINCIPAL = "SERVICE_PRINCIPAL";

    private final IdentityFoundationService foundationService;
    private final ServicePrincipalFoundationService servicePrincipalFoundationService;
    private final ServicePrincipalRepository servicePrincipalRepository;
    private final IdentityControlSettings settings;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public IdentityControlService(
            IdentityFoundationService foundationService,
            ServicePrincipalFoundationService servicePrincipalFoundationService,
            ServicePrincipalRepository servicePrincipalRepository,
            IdentityControlSettings settings,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.foundationService = Objects.requireNonNull(foundationService, "foundationService");
        this.servicePrincipalFoundationService = Objects.requireNonNull(
                servicePrincipalFoundationService, "servicePrincipalFoundationService");
        this.servicePrincipalRepository = Objects.requireNonNull(
                servicePrincipalRepository, "servicePrincipalRepository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 人员账号状态迁移：禁用 / 锁定 / 关闭 / 恢复。 */
    @Transactional
    public LifecycleView transitionAccountStatus(
            UUID accountId, AccountStatus targetStatus, String changeReference, ControlActor actor) {
        requireTrustedActor(actor);
        IdentityFoundationService.AccountStatusTransition transition =
                foundationService.changeAccountStatus(accountId, targetStatus);
        audit(new AuditEntry(
                HUMAN_ACCOUNT, accountId, operation(targetStatus), null,
                transition.previousStatus().name(), transition.current().status().name(),
                transition.previousEpoch(), transition.current().securityEpoch(),
                changeReference, actor));
        return new LifecycleView(
                HUMAN_ACCOUNT, accountId, operation(targetStatus), null,
                transition.previousStatus().name(), transition.current().status().name(),
                transition.previousEpoch(), transition.current().securityEpoch());
    }

    /** 密码轮换：吊销旧材料、写入新 ACTIVE 材料并递增 epoch（同一事务）。 */
    @Transactional
    public LifecycleView rotatePassword(
            UUID accountId, String newPassword, String changeReference, ControlActor actor) {
        requireTrustedActor(actor);
        IdentityFoundationService.PasswordRotation rotation =
                foundationService.rotatePassword(accountId, newPassword);
        audit(new AuditEntry(
                HUMAN_ACCOUNT, accountId, "PASSWORD_ROTATED", CredentialType.PASSWORD.name(),
                rotation.accountStatus().name(), rotation.accountStatus().name(),
                rotation.previousEpoch(), rotation.newEpoch(), changeReference, actor));
        return new LifecycleView(
                HUMAN_ACCOUNT, accountId, "PASSWORD_ROTATED", CredentialType.PASSWORD.name(),
                rotation.accountStatus().name(), rotation.accountStatus().name(),
                rotation.previousEpoch(), rotation.newEpoch());
    }

    /** 凭据撤销：吊销指定类型的 ACTIVE 材料并递增 epoch（同一事务）。 */
    @Transactional
    public LifecycleView revokeCredential(
            UUID accountId, CredentialType credentialType, String changeReference, ControlActor actor) {
        requireTrustedActor(actor);
        IdentityFoundationService.CredentialRevocation revocation =
                foundationService.revokeCredential(accountId, credentialType);
        audit(new AuditEntry(
                HUMAN_ACCOUNT, accountId, "CREDENTIAL_REVOKED", credentialType.name(),
                revocation.accountStatus().name(), revocation.accountStatus().name(),
                revocation.previousEpoch(), revocation.newEpoch(), changeReference, actor));
        return new LifecycleView(
                HUMAN_ACCOUNT, accountId, "CREDENTIAL_REVOKED", credentialType.name(),
                revocation.accountStatus().name(), revocation.accountStatus().name(),
                revocation.previousEpoch(), revocation.newEpoch());
    }

    /** 服务主体状态迁移：禁用 / 恢复（同样递增 epoch，禁用后 token 签发失败关闭）。 */
    @Transactional
    public LifecycleView transitionServicePrincipalStatus(
            UUID principalId,
            ServicePrincipalStatus targetStatus,
            String changeReference,
            ControlActor actor) {
        requireTrustedActor(actor);
        ServicePrincipalFoundationService.PrincipalStatusTransition transition =
                servicePrincipalFoundationService.changePrincipalStatus(principalId, targetStatus);
        audit(new AuditEntry(
                SERVICE_PRINCIPAL, principalId, operation(targetStatus), null,
                transition.previousStatus().name(), transition.current().status().name(),
                transition.previousEpoch(), transition.current().securityEpoch(),
                changeReference, actor));
        return new LifecycleView(
                SERVICE_PRINCIPAL, principalId, operation(targetStatus), null,
                transition.previousStatus().name(), transition.current().status().name(),
                transition.previousEpoch(), transition.current().securityEpoch());
    }

    /**
     * 受信 SERVICE 主体守卫（应用服务层第二道，Controller 已做同一判断）：精确白名单 +
     * ServicePrincipal 当前 ACTIVE + Token {@code sec_epoch} 等于当前 epoch。任一不满足统一
     * 403，不区分原因。
     */
    private void requireTrustedActor(ControlActor actor) {
        if (actor == null || actor.serviceId() == null || actor.requestId() == null
                || actor.requestId().isBlank()
                || !settings.trustedServiceId().equals(actor.serviceId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        UUID principalId;
        try {
            principalId = UUID.fromString(actor.serviceId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        ServicePrincipal principal = servicePrincipalRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        if (!principal.status().canAuthenticate()
                || principal.securityEpoch() != actor.tokenSecurityEpoch()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private void audit(AuditEntry entry) {
        Instant occurredAt = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO ainer_identity_principal_lifecycle_audit
                    (principal_type, principal_id, operation, previous_status, new_status,
                     previous_security_epoch, new_security_epoch, credential_type,
                     actor_service_id, request_id, change_reference, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.principalType(), entry.principalId(), entry.operation(),
                entry.previousStatus(), entry.newStatus(),
                entry.previousSecurityEpoch(), entry.newSecurityEpoch(), entry.credentialType(),
                entry.actor().serviceId(), entry.actor().requestId(), entry.changeReference(),
                Timestamp.from(occurredAt));
    }

    private static String operation(AccountStatus targetStatus) {
        return switch (targetStatus) {
            case ACTIVE -> "RESTORED";
            case LOCKED -> "LOCKED";
            case DISABLED -> "DISABLED";
            case CLOSED -> "CLOSED";
        };
    }

    private static String operation(ServicePrincipalStatus targetStatus) {
        return targetStatus == ServicePrincipalStatus.ACTIVE ? "RESTORED" : "DISABLED";
    }

    /** 已完成操作的前后投影；密码永远不出现在响应里。 */
    public record LifecycleView(
            String principalType,
            UUID principalId,
            String operation,
            String credentialType,
            String previousStatus,
            String status,
            long previousSecurityEpoch,
            long securityEpoch) {
    }

    /** 已验证 SERVICE 调用方的投影：主体标识、Token 携带的 epoch 与请求 ID。 */
    public record ControlActor(String serviceId, long tokenSecurityEpoch, String requestId) {
    }

    private record AuditEntry(
            String principalType,
            UUID principalId,
            String operation,
            String credentialType,
            String previousStatus,
            String newStatus,
            long previousSecurityEpoch,
            long newSecurityEpoch,
            String changeReference,
            ControlActor actor) {
    }
}
